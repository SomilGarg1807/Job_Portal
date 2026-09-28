package com.somil.jobportal.ai.search;

import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.somil.jobportal.ai.*;
import com.somil.jobportal.ai.embedding.*;
import com.somil.jobportal.ai.store.VectorText;
import com.somil.jobportal.ai.text.*;
import com.somil.jobportal.entity.*;

/** Returns empty scores on failure so callers retain their existing keyword/profile ranking. */
public class SemanticJobSearch {
    private static final Logger LOG = LoggerFactory.getLogger(SemanticJobSearch.class);
    private final SemanticVectorRepository vectors;
    private final Embedder queries;
    private final AiEmbeddingProperties properties;
    private final double minimumSimilarity;
    private final Semaphore querySlot = new Semaphore(1);
    private volatile Instant retryAfter = Instant.MIN;
    private final Map<String, Cached> cache = new LinkedHashMap<>(16, .75f, true);
    private final Deque<Instant> queryRequests = new ArrayDeque<>();

    public SemanticJobSearch(SemanticVectorRepository vectors, Embedder queries,
            AiEmbeddingProperties properties, double minimumSimilarity) {
        if (!Double.isFinite(minimumSimilarity) || minimumSimilarity < -1 || minimumSimilarity > 1)
            throw new IllegalArgumentException("Semantic minimum similarity must be between -1 and 1");
        this.vectors = vectors;
        this.queries = queries;
        this.properties = properties;
        this.minimumSimilarity = minimumSimilarity;
    }

    public Map<Integer, Double> recommendations(JobSeekerProfile profile, List<JobPostActivity> jobs) {
        return safely(() -> {
            String text = EmbeddingTexts.candidate(EmbeddingStrategy.CANDIDATE_PROFILE_V1, profile, properties.maxChars());
            if (text.isBlank() || jobs.isEmpty()) return Map.of();
            String hash = SourceHash.of(EmbeddingStrategy.CANDIDATE_PROFILE_V1, properties.model(), properties.dimensions(), text);
            return vectors.candidate(profile.getUserAccountId(), hash, properties.model(), properties.dimensions())
                    .map(vector -> scores(jobs, vector)).orElseGet(Map::of);
        });
    }

    public Map<Integer, Double> search(String query, List<JobPostActivity> jobs) {
        if (query == null || query.isBlank() || query.length() > 500 || jobs.isEmpty()) return Map.of();
        return safely(() -> {
            String vector = queryVector(query.trim().replaceAll("\\s+", " "));
            return vector == null ? Map.of() : scores(jobs, vector);
        });
    }

    private String queryVector(String query) {
        synchronized (cache) {
            Cached hit = cache.get(query);
            if (hit != null && hit.expires().isAfter(Instant.now())) return hit.vector();
        }
        // Bound anonymous traffic and provider outages without blocking other search requests.
        if (Instant.now().isBefore(retryAfter) || !querySlot.tryAcquire()) return null;
        try {
            Instant now = Instant.now();
            while (!queryRequests.isEmpty() && queryRequests.peekFirst().isBefore(now.minusSeconds(60))) queryRequests.removeFirst();
            if (queryRequests.size() >= 30) return null;
            queryRequests.addLast(now);
            String vector = VectorText.format(queries.embed(List.of(new EmbeddingInput(query, "SEMANTIC_SIMILARITY"))).get(0).values());
            synchronized (cache) {
                cache.put(query, new Cached(vector, Instant.now().plusSeconds(600)));
                while (cache.size() > 512) cache.remove(cache.keySet().iterator().next());
            }
            return vector;
        } catch (RuntimeException ex) {
            retryAfter = Instant.now().plusSeconds(60);
            throw ex;
        } finally {
            querySlot.release();
        }
    }

    private Map<Integer, Double> scores(List<JobPostActivity> jobs, String vector) {
        Map<Integer, JobPostActivity> byId = new HashMap<>();
        jobs.forEach(job -> byId.put(job.getJobPostId(), job));
        Map<Integer, Double> scores = new HashMap<>();
        for (var match : vectors.matches(byId.keySet(), vector, properties.model(), properties.dimensions())) {
            JobPostActivity job = byId.get(match.id());
            if (job == null || !Double.isFinite(match.similarity()) || match.similarity() < minimumSimilarity) continue;
            EmbeddingStrategy strategy = EmbeddingStrategy.fromId(match.strategy());
            if (!properties.strategiesFor(EmbeddingTarget.JOB).contains(strategy)) continue;
            String text = EmbeddingTexts.job(strategy, job, properties.maxChars());
            String hash = SourceHash.of(strategy, properties.model(), properties.dimensions(), text);
            if (hash.equals(match.hash())) scores.merge(match.id(), match.similarity(), Math::max);
        }
        return scores;
    }

    private Map<Integer, Double> safely(Supplier<Map<Integer, Double>> work) {
        try { return work.get(); }
        catch (RuntimeException ex) {
            // Do not log search text, profile data, provider payloads or credentials.
            LOG.warn("Semantic matching unavailable ({}); using existing search", ex.getClass().getSimpleName());
            return Map.of();
        }
    }
    private record Cached(String vector, Instant expires) { }
}
