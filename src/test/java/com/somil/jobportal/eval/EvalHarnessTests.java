package com.somil.jobportal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import com.somil.jobportal.ai.embedding.Embedder;
import com.somil.jobportal.ai.embedding.Embedding;
import com.somil.jobportal.ai.embedding.EmbeddingInput;

class EvalHarnessTests {
    @TempDir Path temporary;

    @Test
    void metricsRequireJudgmentsForRetrievedJobs() {
        assertThrows(IllegalArgumentException.class, () -> EvalMetrics.measure(List.of("unknown"), Map.of(), 1, false));
        var result = EvalMetrics.measure(List.of("relevant", "other"), Map.of("relevant", 2, "other", 0), 1, false);
        assertEquals(.2, result.precision5());
        assertEquals(1, result.recall10());
        assertEquals(1, result.mrr());
    }

    @Test
    void diskCacheMakesSecondRunWithoutProviderCalls() {
        AtomicInteger calls = new AtomicInteger();
        Embedder fake = new Embedder() {
            @Override public List<Embedding> embed(List<EmbeddingInput> inputs) {
                calls.incrementAndGet();
                return inputs.stream().map(input -> new Embedding(new float[]{1, 0}, model(), dimensions())).toList();
            }
            @Override public String model() { return "test-model"; }
            @Override public int dimensions() { return 2; }
            @Override public int batchSize() { return 2; }
        };
        var input = List.of(new EmbeddingInput("synthetic text", "SEMANTIC_SIMILARITY"));
        assertEquals(1, new DiskEmbeddingCache(fake, temporary).embed(input).size());
        DiskEmbeddingCache rerun = new DiskEmbeddingCache(fake, temporary);
        assertEquals(1, rerun.embed(input).size());
        assertEquals(1, calls.get());
        assertEquals(1, rerun.avoidedCalls());
    }

    @Test
    void rejectsProductionDatabaseNameBeforeConnecting() {
        var settings = Map.of("EVAL_DB_URL", "jdbc:mysql://localhost:4000/jobportal",
                "EVAL_DB_USERNAME", "dummy", "EVAL_DB_PASSWORD", "dummy", "GEMINI_API_KEY", "dummy");
        assertThrows(IllegalArgumentException.class, () -> EvalConfig.from(settings));
    }

    @Test
    void qualityCheckFailsOnlyOnDropsBeyondTolerance() throws Exception {
        var baseline = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"tolerance\": 0.02, \"metrics\": {\"hybrid/max/0.55\": {\"precision5\": 0.830, \"mrr10\": 0.925}}}");
        assertTrue(EvalCheck.regressions(baseline, Map.of("hybrid/max/0.55",
                Map.of("precision5", 0.815, "mrr10", 0.95))).isEmpty(), "small drop and improvement both pass");
        assertEquals(List.of("hybrid/max/0.55 precision5 0.800 < baseline 0.830"), EvalCheck.regressions(baseline,
                Map.of("hybrid/max/0.55", Map.of("precision5", 0.80, "mrr10", 0.925))));
        assertEquals(2, EvalCheck.regressions(baseline, Map.of()).size(), "a metric that was not measured fails");
    }

    @Test
    void aggregatesAverageEachModeStrategyAndThreshold() {
        List<Map<String, Object>> rows = List.of(
                Map.of("mode", "hybrid", "strategy", "max", "threshold", .55, "precision5", .8, "recall10", .4, "mrr10", 1.0),
                Map.of("mode", "hybrid", "strategy", "max", "threshold", .55, "precision5", .6, "recall10", .2, "mrr10", .5));
        var hybrid = EvalRunner.aggregates(rows).get("hybrid/max/0.55");
        assertEquals(.7, hybrid.get("precision5"), 1e-9);
        assertEquals(.3, hybrid.get("recall10"), 1e-9);
        assertEquals(.75, hybrid.get("mrr10"), 1e-9);
    }

    @Test
    void offlineRunsNeedNoGeminiKey() {
        var settings = Map.of("EVAL_DB_URL", "jdbc:mysql://localhost:4000/jobportal_eval",
                "EVAL_DB_USERNAME", "dummy", "EVAL_DB_PASSWORD", "dummy");
        assertThrows(IllegalArgumentException.class, () -> EvalConfig.from(settings));
        System.setProperty("eval.offline", "true");
        try {
            assertEquals("", EvalConfig.from(settings).apiKey);
        } finally {
            System.clearProperty("eval.offline");
        }
    }

    @Test
    void committedQueryCacheCoversEveryQuery() throws Exception {
        // CI runs offline, so a new or edited query needs its vector committed under eval/query-cache.
        var queries = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Path.of("eval/data/queries.json").toFile());
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (var query : queries.path("queries")) {
            String text = query.path("text").asText().trim().replaceAll("\\s+", " ");
            String hash = java.util.HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertTrue(Files.exists(Path.of("eval/query-cache/gemini-embedding-001/768", hash + ".json")),
                    "Missing cached vector for " + query.path("id").asText() + "; run the eval locally with GEMINI_API_KEY.");
        }
    }

    @Test
    void realTidbSupportsTheEvalVectorQueryWhenConfigured() throws Exception {
        Path file = Path.of(".env.eval");
        Assumptions.assumeTrue(Files.exists(file) || System.getenv("EVAL_DB_URL") != null,
                "No eval database configured; TiDB integration test skipped.");
        EvalConfig config = EvalConfig.load(file);
        var source = EvalRunner.datasource(config);
        EvalRunner.verifyDatabase(source, config.database);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        String type = jdbc.queryForObject("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='ai_job_embedding' AND COLUMN_NAME='embedding'", String.class);
        assertTrue(type != null && type.equalsIgnoreCase("vector(768)"));
        Double similarity = jdbc.queryForObject("SELECT 1 - VEC_COSINE_DISTANCE(CAST(? AS VECTOR), CAST(? AS VECTOR))",
                Double.class, "[1,0]", "[1,0]");
        assertEquals(1.0, similarity, 1e-6);
        Integer evalTableExists = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='eval_job'", Integer.class);
        if (evalTableExists != null && evalTableExists == 1) {
            assertEquals(288, jdbc.queryForObject("SELECT COUNT(*) FROM eval_job", Integer.class));
            assertEquals(576, jdbc.queryForObject("SELECT COUNT(*) FROM ai_job_embedding "
                    + "WHERE job_post_id BETWEEN 900001 AND 900288", Integer.class));
        }
    }
}
