package com.somil.jobportal.ai;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code app.ai.embedding.*}. application.properties maps each one to an
 * environment variable; see ai/README.md for the list.
 */
@ConfigurationProperties("app.ai.embedding")
public record AiEmbeddingProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String apiKey,
        @DefaultValue("gemini-embedding-001") String model,
        @DefaultValue("768") int dimensions,
        @DefaultValue("50") int batchSize,
        @DefaultValue("5") int maxRetries,
        @DefaultValue("2s") Duration initialBackoff,
        @DefaultValue("60s") Duration maxBackoff,
        @DefaultValue("30s") Duration requestTimeout,
        @DefaultValue("6000") int maxChars,
        @DefaultValue("3s") Duration saveDelay,
        @DefaultValue({"job_full_v1", "job_req_v1"}) List<String> jobStrategies,
        @DefaultValue("cand_profile_v1") List<String> candidateStrategies) {

    /** Gemini accepts at most 100 texts in one batchEmbedContents call. */
    public static final int MAX_BATCH_SIZE = 100;

    public AiEmbeddingProperties {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("app.ai.embedding.batch-size must be between 1 and " + MAX_BATCH_SIZE);
        }
        if (maxRetries < 0) throw new IllegalArgumentException("app.ai.embedding.max-retries must not be negative");
    }

    public List<EmbeddingStrategy> strategiesFor(EmbeddingTarget target) {
        return EmbeddingStrategy.parse(target == EmbeddingTarget.JOB ? jobStrategies : candidateStrategies, target);
    }
}
