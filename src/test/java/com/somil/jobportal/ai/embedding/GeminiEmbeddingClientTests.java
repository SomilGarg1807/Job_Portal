package com.somil.jobportal.ai.embedding;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiEmbeddingClientTests {
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents";

    private MockRestServiceServer server;
    private RestClient restClient;
    private final List<Duration> sleeps = new ArrayList<>();

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    private GeminiEmbeddingClient client(int dimensions, int maxRetries) {
        return new GeminiEmbeddingClient(restClient, "test-key", "gemini-embedding-001", dimensions, maxRetries,
                Duration.ofSeconds(2), Duration.ofSeconds(60), sleeps::add, () -> 0.0);
    }

    static String vectors(int count, int dimensions) {
        String vector = IntStream.range(0, dimensions).mapToObj(i -> "0.5").collect(Collectors.joining(","));
        return "{\"embeddings\":[" + IntStream.range(0, count).mapToObj(i -> "{\"values\":[" + vector + "]}")
                .collect(Collectors.joining(",")) + "]}";
    }

    @Test
    void sendsOneBatchRequestWithModelDimensionAndTaskType() {
        server.expect(requestTo(URL))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.requests.length()").value(2))
                .andExpect(jsonPath("$.requests[0].model").value("models/gemini-embedding-001"))
                .andExpect(jsonPath("$.requests[0].content.parts[0].text").value("Java developer"))
                .andExpect(jsonPath("$.requests[0].taskType").value("SEMANTIC_SIMILARITY"))
                .andExpect(jsonPath("$.requests[0].outputDimensionality").value(3))
                .andExpect(jsonPath("$.requests[1].taskType").doesNotExist())
                .andRespond(withSuccess(vectors(2, 3), MediaType.APPLICATION_JSON));

        var response = client(3, 5).embedBatch(List.of(
                new EmbeddingInput("Java developer", "SEMANTIC_SIMILARITY"), new EmbeddingInput("Go developer", null)));

        assertEquals(2, response.vectors().size());
        assertArrayEquals(new float[]{0.5f, 0.5f, 0.5f}, response.vectors().get(0));
        assertNull(response.reportedTokens());
        assertEquals(1, response.attempts());
        server.verify();
    }

    @Test
    void usesTokenCountWhenGeminiReportsOne() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"embeddings\":[{\"values\":[1,0]}],\"usageMetadata\":{\"totalTokenCount\":7}}", MediaType.APPLICATION_JSON));
        assertEquals(7, client(2, 0).embedBatch(List.of(new EmbeddingInput("text", null))).reportedTokens());
    }

    @Test
    void retriesRateLimitWithExponentialBackoffThenSucceeds() {
        server.expect(times(2), anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{}"));
        server.expect(anything()).andRespond(withSuccess(vectors(1, 2), MediaType.APPLICATION_JSON));

        var response = client(2, 5).embedBatch(List.of(new EmbeddingInput("text", null)));

        assertEquals(3, response.attempts());
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)), sleeps);
        server.verify();
    }

    @Test
    void honoursRetryDelayFromGemini() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":429,\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"13s\"}]}}"));
        server.expect(anything()).andRespond(withSuccess(vectors(1, 2), MediaType.APPLICATION_JSON));

        client(2, 5).embedBatch(List.of(new EmbeddingInput("text", null)));

        assertEquals(List.of(Duration.ofSeconds(13)), sleeps);
    }

    @Test
    void honoursRetryAfterHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "9");
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).headers(headers));
        server.expect(anything()).andRespond(withSuccess(vectors(1, 2), MediaType.APPLICATION_JSON));

        client(2, 5).embedBatch(List.of(new EmbeddingInput("text", null)));

        assertEquals(List.of(Duration.ofSeconds(9)), sleeps);
    }

    @Test
    void givesUpAfterMaxRetriesAndReportsQuota() {
        server.expect(times(3), anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{}"));

        var error = assertThrows(EmbeddingException.class,
                () -> client(2, 2).embedBatch(List.of(new EmbeddingInput("text", null))));

        assertTrue(error.isQuotaExhausted());
        assertEquals(2, sleeps.size());
        server.verify();
    }

    @Test
    void dailyQuotaIsNotRetried() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("{\"error\":{\"details\":[{\"violations\":[{\"quotaId\":\"EmbedContentRequestsPerDayPerProject\"}]}]}}"));

        var error = assertThrows(EmbeddingException.class,
                () -> client(2, 5).embedBatch(List.of(new EmbeddingInput("text", null))));

        assertTrue(error.isQuotaExhausted());
        assertTrue(sleeps.isEmpty());
        server.verify();
    }

    @Test
    void badRequestIsNotRetriedAndDoesNotLeakProviderBody() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("provider-private-detail"));

        var error = assertThrows(EmbeddingException.class,
                () -> client(2, 5).embedBatch(List.of(new EmbeddingInput("text", null))));

        assertFalse(error.isQuotaExhausted());
        assertFalse(error.getMessage().contains("provider-private-detail"));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void wrongDimensionIsRejected() {
        server.expect(anything()).andRespond(withSuccess(vectors(1, 3), MediaType.APPLICATION_JSON));

        var error = assertThrows(EmbeddingException.class,
                () -> client(768, 0).embedBatch(List.of(new EmbeddingInput("text", null))));

        assertTrue(error.getMessage().contains("3-dimension"));
    }

    @Test
    void missingApiKeyMakesNoRequest() {
        var noKey = new GeminiEmbeddingClient(restClient, "", "gemini-embedding-001", 2, 0,
                Duration.ofSeconds(1), Duration.ofSeconds(1), sleeps::add, () -> 0.0);
        assertThrows(EmbeddingException.class, () -> noKey.embedBatch(List.of(new EmbeddingInput("text", null))));
        server.verify();
    }

    @Test
    void backoffIsCappedAndJittered() {
        var jittered = new GeminiEmbeddingClient(restClient, "k", "m", 2, 10, Duration.ofSeconds(2), Duration.ofSeconds(60),
                sleeps::add, () -> 1.0);
        assertEquals(Duration.ofMillis(2400), jittered.delay(1, null));
        assertEquals(Duration.ofSeconds(72), jittered.delay(10, null));
    }
}
