package com.somil.jobportal.eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.somil.jobportal.ai.AiEmbeddingProperties;
import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.ai.EmbeddingTarget;
import com.somil.jobportal.ai.embedding.EmbeddingInput;
import com.somil.jobportal.ai.embedding.EmbeddingService;
import com.somil.jobportal.ai.search.SemanticVectorRepository;
import com.somil.jobportal.ai.store.JdbcEmbeddingStore;
import com.somil.jobportal.ai.store.StoredEmbedding;
import com.somil.jobportal.ai.store.VectorText;
import com.somil.jobportal.ai.text.EmbeddingTexts;
import com.somil.jobportal.ai.text.SourceHash;
import com.somil.jobportal.entity.JobCompany;
import com.somil.jobportal.entity.JobLocation;
import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.util.JobExperience;

/** Runs against an explicitly named eval database only; all job fixtures are synthetic. */
final class EvalRunner {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Double> THRESHOLDS = List.of(.35, .45, .55, .65, .75);
    private static final int FIRST_JOB_ID = 900001;

    static void run(EvalConfig config, Path evalRoot) throws Exception {
        AtomicLong apiCalls = new AtomicLong();
        AtomicReference<DiskEmbeddingCache> cacheRef = new AtomicReference<>();
        try {
        JsonNode fixture = JSON.readTree(evalRoot.resolve("data/jobs.json").toFile());
        JsonNode queryFile = JSON.readTree(evalRoot.resolve("data/queries.json").toFile());
        boolean useAgentEstimate = Boolean.getBoolean("eval.useAgentLabels");
        Path labelsPath = evalRoot.resolve(useAgentEstimate
                ? "data/labels-agent-estimate.json" : "data/labels-draft.json");
        JsonNode labels = JSON.readTree(labelsPath.toFile());
        if (!fixture.path("synthetic").asBoolean() || fixture.path("jobs").size() < 250
                || queryFile.path("queries").size() != 40 || labels.path("queries").size() != 40)
            throw new IllegalStateException("Unexpected eval fixture or query count.");
        DataSource datasource = datasource(config);
        verifyDatabase(datasource, config.database);
        JdbcTemplate jdbc = new JdbcTemplate(datasource);
        verifyVectorTable(jdbc, config.dimensions);
        Map<String, Job> jobs = jobs(fixture.path("jobs"));
        prepareJobs(jdbc, jobs.values());
        AiEmbeddingProperties properties = properties(config);
        ClientHttpRequestInterceptor countCalls = (request, body, execution) -> {
            apiCalls.incrementAndGet();
            return execution.execute(request, body);
        };
        EmbeddingService provider = new EmbeddingService(properties, RestClient.builder().requestInterceptor(countCalls));
        DiskEmbeddingCache cache = new DiskEmbeddingCache(provider, evalRoot.resolve("cache"));
        cacheRef.set(cache);
        indexJobs(jdbc, jobs.values(), cache, config);
        SemanticVectorRepository vectors = new SemanticVectorRepository(jdbc);
        List<EmbeddingInput> queryInputs = new ArrayList<>();
        Map<String, Boolean> queryNeededCall = new HashMap<>();
        for (JsonNode query : queryFile.path("queries")) {
            EmbeddingInput input = new EmbeddingInput(query.path("text").asText().trim().replaceAll("\\s+", " "),
                    "SEMANTIC_SIMILARITY");
            queryInputs.add(input);
            queryNeededCall.put(query.path("id").asText(), !cache.contains(input));
        }
        // One shared provider batch covers all 40 queries. Per-query rows record participation in that batch.
        cache.embed(queryInputs);
        Map<String, JsonNode> labelMap = new HashMap<>();
        for (JsonNode item : labels.path("queries")) labelMap.put(item.path("queryId").asText(), item);
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> pending = new HashSet<>();
        for (JsonNode query : queryFile.path("queries")) {
            String id = query.path("id").asText();
            JsonNode label = labelMap.get(id);
            if (label == null) throw new IllegalStateException("Missing labels for " + id);
            SearchContext searchContext = retrieve(jdbc, vectors, cache, jobs, query, config);
            for (double threshold : THRESHOLDS) {
                for (String mode : List.of("keyword", "semantic", "hybrid")) {
                    long beforeCalls = apiCalls.get();
                    long start = System.nanoTime();
                    List<Job> ranking = search(searchContext, mode, threshold);
                    long rankingMs = (System.nanoTime() - start) / 1_000_000;
                    long retrievalMs = switch (mode) {
                        case "keyword" -> searchContext.keywordMs();
                        case "semantic" -> searchContext.eligibleMs() + searchContext.vectorMs();
                        default -> searchContext.keywordMs() + searchContext.eligibleMs() + searchContext.vectorMs();
                    };
                    List<String> keys = ranking.stream().map(Job::key).toList();
                    Map<String, Integer> grades = new HashMap<>();
                    label.path("judgments").fields().forEachRemaining(entry -> grades.put(entry.getKey(), entry.getValue().asInt()));
                    keys.stream().limit(10).filter(key -> !grades.containsKey(key)).forEach(key -> pending.add(id + "," + key));
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("queryId", id); row.put("category", query.path("category").asText());
                    row.put("mode", mode); row.put("threshold", threshold);
                    row.put("latencyMs", retrievalMs + rankingMs); row.put("retrievalMs", retrievalMs);
                    row.put("rankingMs", rankingMs); row.put("geminiCalls", apiCalls.get() - beforeCalls);
                    row.put("geminiBatchParticipation", queryNeededCall.get(id) ? 1 : 0);
                    row.put("top10", keys.stream().limit(10).toList());
                    row.put("maxSimilarity", searchContext.rawScores().values().stream().mapToDouble(Double::doubleValue)
                            .max().orElse(-1));
                    Map<String, Double> topScores = new LinkedHashMap<>();
                    keys.stream().limit(10).forEach(key -> {
                        Double score = searchContext.rawScores().get(jobs.get(key).id());
                        if (score != null) topScores.put(key, score);
                    });
                    row.put("top10Similarities", topScores);
                    rows.add(row);
                }
            }
            System.out.println("Evaluated " + id + " (" + rows.size() / 15 + "/40 queries).");
        }
        boolean coverageReviewed = labels.path("coverageReviewed").asBoolean(false);
        if (coverageReviewed && pending.isEmpty()) addMetrics(rows, labelMap);
        writeResults(evalRoot, config, jobs, queryFile, labels, rows, pending, apiCalls.get(), cache.hits(), cache.avoidedCalls(),
                coverageReviewed && pending.isEmpty());
        System.out.println("Supplemental judgments needed=" + pending.size() + ". Metrics "
                + (coverageReviewed && pending.isEmpty()
                        ? ("agent".equals(labels.path("coverageReviewedBy").asText())
                                ? "computed from provisional agent estimates." : "computed from reviewed labels.")
                        : "withheld until recall coverage review."));
        } finally {
            DiskEmbeddingCache cache = cacheRef.get();
            System.out.println("Gemini API calls made=" + apiCalls.get() + ", avoided by disk cache="
                    + (cache == null ? 0 : cache.avoidedCalls()) + " ("
                    + (cache == null ? 0 : cache.hits()) + " text hits).");
        }
    }

    static DataSource datasource(EvalConfig config) {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(config.url); source.setUsername(config.username); source.setPassword(config.password);
        return source;
    }

    static void verifyDatabase(DataSource source, String expected) throws Exception {
        try (Connection connection = source.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next() || !expected.equals(result.getString(1)) || !expected.toLowerCase().contains("eval"))
                throw new IllegalStateException("Connected database is not the named eval database.");
        }
    }

    private static void verifyVectorTable(JdbcTemplate jdbc, int dimension) {
        String type = jdbc.queryForObject("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='ai_job_embedding' AND COLUMN_NAME='embedding'", String.class);
        if (type == null || !type.equalsIgnoreCase("vector(" + dimension + ")"))
            throw new IllegalStateException("ai_job_embedding VECTOR dimension differs from eval embedding dimension.");
    }

    private static Map<String, Job> jobs(JsonNode array) {
        Map<String, Job> result = new LinkedHashMap<>();
        int id = FIRST_JOB_ID;
        for (JsonNode node : array) {
            String key = node.path("key").asText();
            if (result.containsKey(key)) throw new IllegalStateException("Duplicate synthetic job key.");
            JobPostActivity entity = new JobPostActivity();
            entity.setJobPostId(id);
            entity.setJobTitle(node.path("title").asText());
            entity.setDescriptionOfJob(node.path("descriptionOfJob").asText());
            entity.setJobType(node.path("jobType").asText());
            entity.setRemote(node.path("remote").asText());
            entity.setMinExperienceYears(node.path("minExperienceYears").asInt());
            entity.setMaxExperienceYears(node.path("maxExperienceYears").asInt());
            entity.setPostedDate(java.util.Date.from(LocalDate.parse(node.path("postedDate").asText()).atStartOfDay().toInstant(ZoneOffset.UTC)));
            JsonNode location = node.path("location");
            entity.setJobLocationId(new JobLocation(null, location.path("city").asText(),
                    location.path("state").asText(), location.path("country").asText()));
            entity.setJobCompanyId(new JobCompany(null, node.path("company").asText(), null));
            result.put(key, new Job(id++, key, entity));
        }
        return result;
    }

    private static void prepareJobs(JdbcTemplate jdbc, java.util.Collection<Job> jobs) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS eval_job (id INT PRIMARY KEY, job_key VARCHAR(80) NOT NULL UNIQUE, "
                + "title VARCHAR(255) NOT NULL, description_html TEXT NOT NULL, company VARCHAR(255) NOT NULL, "
                + "city VARCHAR(100), state VARCHAR(100), country VARCHAR(100), job_type VARCHAR(40), "
                + "remote VARCHAR(40), min_experience INT, max_experience INT, posted_date DATE NOT NULL)");
        Map<Integer, String> expected = jobs.stream().collect(Collectors.toMap(Job::id, Job::key));
        jdbc.query("SELECT id, job_key FROM eval_job", rs -> {
            if (!rs.getString("job_key").equals(expected.get(rs.getInt("id"))))
                throw new IllegalStateException("Eval job table contains an unexpected ID or key; stopped before seeding.");
        });
        jdbc.batchUpdate("INSERT INTO eval_job (id,job_key,title,description_html,company,city,state,country,job_type,remote,"
                + "min_experience,max_experience,posted_date) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "
                + "title=VALUES(title),description_html=VALUES(description_html),company=VALUES(company),city=VALUES(city),"
                + "state=VALUES(state),country=VALUES(country),job_type=VALUES(job_type),remote=VALUES(remote),"
                + "min_experience=VALUES(min_experience),max_experience=VALUES(max_experience),posted_date=VALUES(posted_date)",
                jobs, jobs.size(), (ps, job) -> {
                    JobPostActivity j = job.entity();
                    ps.setInt(1, job.id()); ps.setString(2, job.key()); ps.setString(3, j.getJobTitle());
                    ps.setString(4, j.getDescriptionOfJob()); ps.setString(5, j.getJobCompanyId().getName());
                    ps.setString(6, j.getJobLocationId().getCity()); ps.setString(7, j.getJobLocationId().getState());
                    ps.setString(8, j.getJobLocationId().getCountry()); ps.setString(9, j.getJobType()); ps.setString(10, j.getRemote());
                    ps.setInt(11, j.getMinExperienceYears()); ps.setInt(12, j.getMaxExperienceYears());
                    ps.setDate(13, new java.sql.Date(j.getPostedDate().getTime()));
                });
    }

    private static AiEmbeddingProperties properties(EvalConfig config) {
        return new AiEmbeddingProperties(true, config.apiKey, config.model, config.dimensions, config.batchSize, 5,
                Duration.ofSeconds(2), Duration.ofSeconds(60), Duration.ofSeconds(30), 6000,
                Duration.ZERO, List.of("job_full_v1", "job_req_v1"), List.of("cand_profile_v1"));
    }

    private static void indexJobs(JdbcTemplate jdbc, java.util.Collection<Job> jobs, DiskEmbeddingCache cache, EvalConfig config) {
        JdbcEmbeddingStore store = new JdbcEmbeddingStore(jdbc);
        Map<com.somil.jobportal.ai.store.EmbeddingKey, String> hashes = store.hashes(EmbeddingTarget.JOB,
                jobs.stream().map(Job::id).toList());
        List<PendingEmbedding> missing = new ArrayList<>();
        for (Job job : jobs) for (EmbeddingStrategy strategy : List.of(EmbeddingStrategy.JOB_FULL_V1, EmbeddingStrategy.JOB_REQUIREMENTS_V1)) {
            String text = EmbeddingTexts.job(strategy, job.entity(), 6000);
            String hash = SourceHash.of(strategy, config.model, config.dimensions, text);
            var key = new com.somil.jobportal.ai.store.EmbeddingKey(job.id(), strategy.id());
            if (!hash.equals(hashes.get(key))) missing.add(new PendingEmbedding(job.id(), strategy, text, hash));
        }
        for (int start = 0; start < missing.size(); start += config.batchSize) {
            List<PendingEmbedding> batch = missing.subList(start, Math.min(start + config.batchSize, missing.size()));
            var vectors = cache.embed(batch.stream().map(p -> new EmbeddingInput(p.text(), p.strategy().taskType())).toList());
            List<StoredEmbedding> rows = new ArrayList<>();
            for (int i = 0; i < batch.size(); i++) {
                PendingEmbedding p = batch.get(i);
                rows.add(new StoredEmbedding(p.id(), p.strategy().id(), config.model, config.dimensions,
                        p.hash(), p.text().length(), vectors.get(i).values()));
            }
            store.upsert(EmbeddingTarget.JOB, rows);
        }
    }

    private static SearchContext retrieve(JdbcTemplate jdbc, SemanticVectorRepository vectors, DiskEmbeddingCache cache,
            Map<String, Job> jobs, JsonNode query, EvalConfig config) {
        String phrase = query.path("text").asText();
        JsonNode filters = query.path("filters");
        long began = System.nanoTime();
        List<Job> eligible = filtered(jdbc, jobs, "", filters);
        long eligibleMs = (System.nanoTime() - began) / 1_000_000;
        began = System.nanoTime();
        List<Job> keywords = filtered(jdbc, jobs, phrase, filters);
        long keywordMs = (System.nanoTime() - began) / 1_000_000;
        began = System.nanoTime();
        Map<Integer, Double> scores = new HashMap<>();
        if (!eligible.isEmpty()) {
            String normalized = phrase.trim().replaceAll("\\s+", " ");
            String vector = VectorText.format(cache.embed(List.of(new EmbeddingInput(normalized, "SEMANTIC_SIMILARITY"))).get(0).values());
            Map<Integer, Job> byId = eligible.stream().collect(Collectors.toMap(Job::id, j -> j));
            for (var match : vectors.matches(byId.keySet(), vector, config.model, config.dimensions)) {
                Job job = byId.get(match.id());
                if (job == null || !Double.isFinite(match.similarity())) continue;
                EmbeddingStrategy strategy = EmbeddingStrategy.fromId(match.strategy());
                String text = EmbeddingTexts.job(strategy, job.entity(), 6000);
                if (SourceHash.of(strategy, config.model, config.dimensions, text).equals(match.hash()))
                    scores.merge(match.id(), match.similarity(), Math::max);
            }
        }
        long vectorMs = (System.nanoTime() - began) / 1_000_000;
        return new SearchContext(eligible, keywords, scores, eligibleMs, keywordMs, vectorMs);
    }

    private static List<Job> search(SearchContext context, String mode, double threshold) {
        List<Job> eligible = context.eligible();
        List<Job> keywords = context.keywords();
        Map<Integer, Double> scores = context.rawScores().entrySet().stream()
                .filter(entry -> entry.getValue() >= threshold)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Comparator<Job> newest = Comparator.comparing((Job j) -> j.entity().getPostedDate(), Comparator.reverseOrder())
                .thenComparing(Job::id, Comparator.reverseOrder());
        if (mode.equals("keyword")) return keywords.stream().sorted(newest).toList();
        if (mode.equals("semantic")) return eligible.stream().filter(j -> scores.containsKey(j.id()))
                .sorted(Comparator.comparingDouble((Job j) -> scores.get(j.id())).reversed().thenComparing(newest)).toList();
        Map<Integer, Job> combined = new LinkedHashMap<>();
        keywords.forEach(j -> { combined.put(j.id(), j); scores.put(j.id(), scores.getOrDefault(j.id(), 0.0) + 1.0); });
        eligible.stream().filter(j -> scores.containsKey(j.id())).forEach(j -> combined.putIfAbsent(j.id(), j));
        return combined.values().stream().sorted(Comparator.comparingDouble((Job j) -> scores.getOrDefault(j.id(), -1.0))
                .reversed().thenComparing(newest)).toList();
    }

    private static List<Job> filtered(JdbcTemplate jdbc, Map<String, Job> jobs, String phrase, JsonNode filters) {
        String location = filters.path("location").asText("");
        String type = filters.path("jobType").asText("");
        String remote = filters.path("remote").asText("");
        String sql = "SELECT job_key FROM eval_job WHERE "
                + "(LOWER(COALESCE(title,'')) LIKE LOWER(CONCAT('%',?,'%')) OR "
                + "LOWER(COALESCE(description_html,'')) LIKE LOWER(CONCAT('%',?,'%')) OR "
                + "LOWER(COALESCE(company,'')) LIKE LOWER(CONCAT('%',?,'%'))) AND "
                + "(LOWER(COALESCE(city,'')) LIKE LOWER(CONCAT('%',?,'%')) OR "
                + "LOWER(COALESCE(state,'')) LIKE LOWER(CONCAT('%',?,'%')) OR "
                + "LOWER(COALESCE(country,'')) LIKE LOWER(CONCAT('%',?,'%'))) AND "
                + "(?='' OR LOWER(TRIM(job_type))=LOWER(?)) AND (?='' OR LOWER(TRIM(remote))=LOWER(?))";
        List<Job> selected = jdbc.query(sql, (rs, n) -> jobs.get(rs.getString(1)),
                phrase, phrase, phrase, location, location, location, type, type, remote, remote);
        if (selected.stream().anyMatch(j -> j == null)) throw new IllegalStateException("Eval DB contains an unexpected job key.");
        Set<Integer> kept = JobExperience.filter(selected.stream().map(Job::entity).toList(),
                filters.path("experience").asText("")).stream().map(JobPostActivity::getJobPostId).collect(Collectors.toSet());
        return selected.stream().filter(job -> kept.contains(job.id())).toList();
    }

    private static void addMetrics(List<Map<String, Object>> rows, Map<String, JsonNode> labels) {
        for (Map<String, Object> row : rows) {
            JsonNode label = labels.get(row.get("queryId"));
            Map<String, Integer> grades = new HashMap<>();
            label.path("judgments").fields().forEachRemaining(entry -> grades.put(entry.getKey(), entry.getValue().asInt()));
            boolean noAnswer = "no_answer".equals(row.get("category"));
            int relevantTotal = (int) grades.values().stream().filter(grade -> grade > 0).count();
            if (!noAnswer && relevantTotal == 0) throw new IllegalStateException("No positive labels for a non-no-answer query.");
            EvalMetrics.Result metrics = EvalMetrics.measure((List<String>) row.get("top10"), grades, relevantTotal, noAnswer);
            row.put("precision5", metrics.precision5()); row.put("recall10", metrics.recall10());
            row.put("mrr10", metrics.mrr()); row.put("correctNoAnswer", metrics.correctNoAnswer());
        }
    }

    private static void writeResults(Path root, EvalConfig config, Map<String, Job> jobs, JsonNode queries, JsonNode labels,
            List<Map<String, Object>> rows, Set<String> pending, long calls, long hits, long avoided, boolean ready) throws Exception {
        Path out = root.resolve("results"); Files.createDirectories(out);
        int datasetSize = jobs.size();
        String commit = new String(new ProcessBuilder("git", "rev-parse", "HEAD").start().getInputStream().readAllBytes()).trim();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("gitCommit", commit); header.put("model", config.model); header.put("dimension", config.dimensions);
        header.put("datasetSize", datasetSize); header.put("queryCount", queries.path("queries").size());
        header.put("timestampUtc", Instant.now().toString()); header.put("apiCallsMade", calls);
        header.put("cacheTextHits", hits); header.put("apiCallsAvoided", avoided);
        header.put("labelsStatus", labels.path("reviewStatus").asText());
        header.put("metricsStatus", ready
                ? ("agent".equals(labels.path("coverageReviewedBy").asText())
                        ? "PROVISIONAL: AGENT-ESTIMATED LABELS, NOT HUMAN GROUND TRUTH" : "REVIEWED")
                : "WITHHELD: supplemental result judgments and recall coverage review required");
        JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("stage3-results.json").toFile(), Map.of("header", header, "perQuery", rows));
        List<String> csv = new ArrayList<>(List.of("queryId,category,mode,threshold,latencyMs,geminiCalls,geminiBatchParticipation,precision5,recall10,mrr10,correctNoAnswer,top10"));
        for (Map<String, Object> row : rows) csv.add(String.join(",", row.get("queryId").toString(), row.get("category").toString(),
                row.get("mode").toString(), row.get("threshold").toString(), row.get("latencyMs").toString(),
                row.get("geminiCalls").toString(), row.get("geminiBatchParticipation").toString(),
                value(row,"precision5"), value(row,"recall10"), value(row,"mrr10"),
                value(row,"correctNoAnswer"), "\"" + String.join("|", (List<String>) row.get("top10")) + "\""));
        Files.write(out.resolve("stage3-results.csv"), csv);
        Map<String, JsonNode> byQuery = new HashMap<>();
        queries.path("queries").forEach(query -> byQuery.put(query.path("id").asText(), query));
        List<String> review = new ArrayList<>(List.of("queryId,queryText,jobKey,title,city,experience,jobType,remote,reviewedGrade,reviewNote"));
        pending.stream().sorted().forEach(item -> {
            String[] parts = item.split(",", 2);
            JobPostActivity job = jobs.get(parts[1]).entity();
            review.add(String.join(",", csv(parts[0]), csv(byQuery.get(parts[0]).path("text").asText()), csv(parts[1]),
                    csv(job.getJobTitle()), csv(job.getJobLocationId().getCity()), csv(job.getExperienceLabel()),
                    csv(job.getJobType()), csv(job.getRemote()), "", ""));
        });
        Files.write(out.resolve("supplemental-review.csv"), review);
        List<String> md = new ArrayList<>(List.of(ready
                ? "# Semantic search evaluation — provisional results" : "# Semantic search evaluation — pending review", "",
                "Commit: `" + commit + "` · Model: `" + config.model + "` · Dimension: " + config.dimensions
                        + " · Jobs: " + datasetSize + " · UTC: " + header.get("timestampUtc"), "",
                ready ? ("agent".equals(labels.path("coverageReviewedBy").asText())
                        ? "**Provisional agent estimate.** These labels are not human ground truth; do not treat threshold comparisons as proof."
                        : "Metrics use reviewed labels. Synthetic data and a single labeler remain limitations.")
                        : "**Metrics withheld.** Retrieved results have " + pending.size() + " unjudged query/job pairs."
                        + " Recall@10 also requires review of potentially relevant jobs outside the result pool.", "",
                "| Mode | Threshold | P@5 | R@10 | MRR@10 | No-answer accuracy | p50 ms | p95 ms |",
                "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"));
        for (String mode : List.of("keyword", "semantic", "hybrid")) for (double threshold : THRESHOLDS) {
            List<Long> times = rows.stream().filter(r -> mode.equals(r.get("mode")) && threshold == (double) r.get("threshold"))
                    .map(r -> (Long) r.get("latencyMs")).toList();
            List<Map<String, Object>> group = rows.stream().filter(r -> mode.equals(r.get("mode")) && threshold == (double) r.get("threshold")).toList();
            String metrics = ready ? String.format(java.util.Locale.ROOT, "%.3f | %.3f | %.3f | %.3f",
                    average(group, "precision5"), average(group, "recall10"), average(group, "mrr10"),
                    group.stream().filter(r -> "no_answer".equals(r.get("category")))
                            .filter(r -> Boolean.TRUE.equals(r.get("correctNoAnswer"))).count()
                            / (double) group.stream().filter(r -> "no_answer".equals(r.get("category"))).count())
                    : "pending | pending | pending | pending";
            md.add("| " + mode + " | " + threshold + " | " + metrics + " | "
                    + EvalMetrics.percentile(times, .50) + " | " + EvalMetrics.percentile(times, .95) + " |");
        }
        Files.write(out.resolve("stage3-results.md"), md);
    }

    private static String value(Map<String, Object> row, String key) { return row.containsKey(key) ? row.get(key).toString() : ""; }
    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    private static double average(List<Map<String, Object>> rows, String key) {
        return rows.stream().mapToDouble(row -> (Double) row.get(key)).average().orElse(0);
    }

    private record Job(int id, String key, JobPostActivity entity) { }
    private record SearchContext(List<Job> eligible, List<Job> keywords, Map<Integer, Double> rawScores,
                                 long eligibleMs, long keywordMs, long vectorMs) { }
    private record PendingEmbedding(int id, EmbeddingStrategy strategy, String text, String hash) { }
}
