package com.somil.jobportal.ai.embedding;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * One {@code batchEmbedContents} call to the Gemini API, retried with exponential backoff
 * on rate limits (429) and transient server errors (500, 503).
 *
 * <p>When Gemini says how long to wait (a RetryInfo detail or a Retry-After header) that
 * delay is used instead of our own. A 429 that names a per-day quota is not retried at all:
 * waiting seconds will not help and each retry would spend another request.
 */
class GeminiEmbeddingClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiEmbeddingClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Vectors in input order, the token count if Gemini reported one, and how many HTTP attempts it took. */
    record BatchResponse(List<float[]> vectors, Integer reportedTokens, int attempts) {
    }

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final int maxRetries;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Sleeper sleeper;
    private final DoubleSupplier jitter;

    GeminiEmbeddingClient(RestClient client, String apiKey, String model, int dimensions, int maxRetries,
                          Duration initialBackoff, Duration maxBackoff, Sleeper sleeper, DoubleSupplier jitter) {
        this.client = client;
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.sleeper = sleeper;
        this.jitter = jitter;
    }

    static DoubleSupplier randomJitter() {
        return () -> ThreadLocalRandom.current().nextDouble();
    }

    BatchResponse embedBatch(List<EmbeddingInput> inputs) {
        if (!StringUtils.hasText(apiKey)) {
            throw new EmbeddingException("GEMINI_API_KEY is not set, so nothing can be embedded.", false);
        }
        Map<String, Object> body = Map.of("requests", inputs.stream().map(this::request).toList());
        for (int attempt = 1; ; attempt++) {
            try {
                JsonNode response = client.post().uri("/models/{model}:batchEmbedContents", model)
                        .header("x-goog-api-key", apiKey).contentType(MediaType.APPLICATION_JSON)
                        .body(body).retrieve().body(JsonNode.class);
                return parse(response, inputs.size(), attempt);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                String error = ex.getResponseBodyAsString();
                if (status == 429 && error.contains("PerDay")) {
                    throw new EmbeddingException("Gemini's daily embedding quota is used up. Try again after it resets.", true);
                }
                if (status != 429 && status != 500 && status != 503) {
                    // Deliberately not including the body: it can echo request content.
                    throw new EmbeddingException("Gemini rejected the embedding request with HTTP " + status
                            + ". Check GEMINI_API_KEY, GEMINI_EMBEDDING_MODEL and GEMINI_EMBEDDING_DIMENSIONS.", false);
                }
                if (attempt > maxRetries) {
                    throw new EmbeddingException("Gemini still returned HTTP " + status + " after " + maxRetries
                            + " retries.", status == 429);
                }
                Duration delay = delay(attempt, serverDelay(error, ex.getResponseHeaders()));
                LOGGER.warn("Gemini embedding returned HTTP {}; retry {}/{} in {} ms", status, attempt, maxRetries, delay.toMillis());
                pause(delay);
            } catch (ResourceAccessException ex) {
                if (attempt > maxRetries) throw new EmbeddingException("Gemini could not be reached.", ex);
                Duration delay = delay(attempt, null);
                LOGGER.warn("Gemini embedding request failed to connect; retry {}/{} in {} ms", attempt, maxRetries, delay.toMillis());
                pause(delay);
            }
        }
    }

    private Map<String, Object> request(EmbeddingInput input) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", "models/" + model);
        request.put("content", Map.of("parts", List.of(Map.of("text", input.text()))));
        if (input.taskType() != null) request.put("taskType", input.taskType());
        request.put("outputDimensionality", dimensions);
        return request;
    }

    private BatchResponse parse(JsonNode response, int expected, int attempts) {
        JsonNode embeddings = response == null ? null : response.path("embeddings");
        if (embeddings == null || !embeddings.isArray() || embeddings.size() != expected) {
            throw new EmbeddingException("Gemini returned " + (embeddings == null ? 0 : embeddings.size())
                    + " embeddings for " + expected + " texts.", false);
        }
        List<float[]> vectors = new ArrayList<>(expected);
        for (JsonNode embedding : embeddings) {
            JsonNode values = embedding.path("values");
            if (values.size() != dimensions) {
                // Storing a wrong-sized vector would fail in TiDB anyway; fail here with a clear reason.
                throw new EmbeddingException("Gemini returned a " + values.size() + "-dimension vector but "
                        + dimensions + " was requested. Nothing from this batch was stored.", false);
            }
            float[] vector = new float[values.size()];
            for (int i = 0; i < vector.length; i++) vector[i] = (float) values.get(i).asDouble();
            vectors.add(vector);
        }
        return new BatchResponse(vectors, reportedTokens(response), attempts);
    }

    /** Gemini's embedding response may not report usage; when it does, prefer it over our estimate. */
    private static Integer reportedTokens(JsonNode response) {
        JsonNode usage = response.path("usageMetadata");
        for (String field : List.of("totalTokenCount", "promptTokenCount")) {
            if (usage.path(field).canConvertToInt()) return usage.path(field).asInt();
        }
        return null;
    }

    Duration delay(int attempt, Duration serverDelay) {
        double spread = 1 + 0.2 * jitter.getAsDouble();
        if (serverDelay != null) return Duration.ofMillis((long) (serverDelay.toMillis() * spread));
        long base = initialBackoff.toMillis() << Math.min(attempt - 1, 20);
        return Duration.ofMillis((long) (Math.min(base, maxBackoff.toMillis()) * spread));
    }

    private static Duration serverDelay(String errorBody, HttpHeaders headers) {
        try {
            for (JsonNode detail : JSON.readTree(errorBody).path("error").path("details")) {
                String delay = detail.path("retryDelay").asText("");
                if (delay.endsWith("s")) return Duration.ofMillis((long) (Double.parseDouble(delay.substring(0, delay.length() - 1)) * 1000));
            }
        } catch (Exception ignored) {
            // Not JSON, or no RetryInfo: fall back to the header or our own backoff.
        }
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null && retryAfter.trim().matches("\\d+")) return Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
        return null;
    }

    private void pause(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("Interrupted while waiting to retry Gemini.", ex);
        }
    }
}
