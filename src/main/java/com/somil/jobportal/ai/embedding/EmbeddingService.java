package com.somil.jobportal.ai.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.somil.jobportal.ai.AiEmbeddingProperties;

/**
 * Embeds texts with the Gemini embedding API.
 *
 * <ul>
 *   <li>Splits the input into batches of {@code batch-size} texts, one HTTP call each.</li>
 *   <li>Retries rate limits with backoff (see {@link GeminiEmbeddingClient}).</li>
 *   <li>Checks every vector has the configured dimension and scales it to unit length.</li>
 *   <li>Logs texts, characters, tokens and latency for every batch, and keeps running totals.</li>
 * </ul>
 *
 * <p>This is only called when a job or profile is saved and from the backfill, never while
 * a user is searching.
 */
public class EmbeddingService implements Embedder {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingService.class);

    private final GeminiEmbeddingClient client;
    private final String model;
    private final int dimensions;
    private final int batchSize;

    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong texts = new AtomicLong();
    private final AtomicLong characters = new AtomicLong();
    private final AtomicLong tokens = new AtomicLong();
    private final AtomicLong estimatedTokens = new AtomicLong();

    public EmbeddingService(AiEmbeddingProperties properties, RestClient.Builder builder) {
        this(new GeminiEmbeddingClient(restClient(properties, builder), properties.apiKey(), properties.model(),
                        properties.dimensions(), properties.maxRetries(), properties.initialBackoff(),
                        properties.maxBackoff(), Sleeper.REAL, GeminiEmbeddingClient.randomJitter()),
                properties.model(), properties.dimensions(), properties.batchSize());
    }

    EmbeddingService(GeminiEmbeddingClient client, String model, int dimensions, int batchSize) {
        this.client = client;
        this.model = model;
        this.dimensions = dimensions;
        this.batchSize = batchSize;
    }

    private static RestClient restClient(AiEmbeddingProperties properties, RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(properties.requestTimeout());
        return builder.requestFactory(factory).baseUrl("https://generativelanguage.googleapis.com/v1beta").build();
    }

    @Override
    public List<Embedding> embed(List<EmbeddingInput> inputs) {
        List<Embedding> result = new ArrayList<>(inputs.size());
        for (int start = 0; start < inputs.size(); start += batchSize) {
            List<EmbeddingInput> batch = inputs.subList(start, Math.min(start + batchSize, inputs.size()));
            long began = System.nanoTime();
            GeminiEmbeddingClient.BatchResponse response = client.embedBatch(batch);
            long latencyMs = (System.nanoTime() - began) / 1_000_000;

            int chars = batch.stream().mapToInt(input -> input.text().length()).sum();
            boolean reported = response.reportedTokens() != null;
            int batchTokens = reported ? response.reportedTokens() : estimateTokens(chars);
            record(batch.size(), chars, batchTokens, reported);
            LOGGER.info("Embedding batch: model={} dims={} texts={} chars={} tokens={} ({}) latencyMs={} attempts={}",
                    model, dimensions, batch.size(), chars, batchTokens, reported ? "reported" : "estimated",
                    latencyMs, response.attempts());

            for (float[] vector : response.vectors()) {
                result.add(new Embedding(normalize(vector), model, dimensions));
            }
        }
        return result;
    }

    /** Roughly four characters per token for English text; used only when Gemini reports no count. */
    static int estimateTokens(int chars) {
        return (chars + 3) / 4;
    }

    /**
     * Cosine similarity ignores length, but Gemini only guarantees unit length at the full 3072
     * dimensions. Normalising keeps stored vectors comparable by any distance function later.
     */
    static float[] normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) sum += v * (double) v;
        if (sum == 0) throw new EmbeddingException("Gemini returned an all-zero vector.", false);
        double norm = Math.sqrt(sum);
        float[] unit = new float[vector.length];
        for (int i = 0; i < vector.length; i++) unit[i] = (float) (vector[i] / norm);
        return unit;
    }

    private void record(int batchTexts, int chars, int batchTokens, boolean reported) {
        requests.incrementAndGet();
        texts.addAndGet(batchTexts);
        characters.addAndGet(chars);
        (reported ? tokens : estimatedTokens).addAndGet(batchTokens);
    }

    /** Running totals since startup, for the backfill summary. */
    public Usage usage() {
        return new Usage(requests.get(), texts.get(), characters.get(), tokens.get(), estimatedTokens.get());
    }

    public record Usage(long requests, long texts, long characters, long reportedTokens, long estimatedTokens) {
        @Override
        public String toString() {
            return "requests=" + requests + " texts=" + texts + " chars=" + characters
                    + " tokens(reported)=" + reportedTokens + " tokens(estimated)=" + estimatedTokens;
        }
    }

    @Override
    public String model() { return model; }

    @Override
    public int dimensions() { return dimensions; }

    @Override
    public int batchSize() { return batchSize; }
}
