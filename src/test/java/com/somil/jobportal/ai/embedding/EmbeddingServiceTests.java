package com.somil.jobportal.ai.embedding;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EmbeddingServiceTests {

    @Test
    void splitsInputIntoBatchesKeepsOrderAndNormalises() {
        var builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(jsonPath("$.requests.length()").value(2))
                .andRespond(withSuccess("{\"embeddings\":[{\"values\":[3,4]},{\"values\":[0,2]}]}", MediaType.APPLICATION_JSON));
        server.expect(jsonPath("$.requests.length()").value(2))
                .andRespond(withSuccess("{\"embeddings\":[{\"values\":[1,0]},{\"values\":[0,1]}]}", MediaType.APPLICATION_JSON));
        server.expect(jsonPath("$.requests.length()").value(1))
                .andRespond(withSuccess("{\"embeddings\":[{\"values\":[5,0]}],\"usageMetadata\":{\"totalTokenCount\":3}}",
                        MediaType.APPLICATION_JSON));
        var client = new GeminiEmbeddingClient(builder.build(), "key", "gemini-embedding-001", 2, 0,
                Duration.ofSeconds(1), Duration.ofSeconds(1), duration -> { }, () -> 0.0);
        var service = new EmbeddingService(client, "gemini-embedding-001", 2, 2);

        List<Embedding> result = service.embed(List.of(new EmbeddingInput("aaaa", null), new EmbeddingInput("bbbb", null),
                new EmbeddingInput("cccc", null), new EmbeddingInput("dddd", null), new EmbeddingInput("eeeeeeee", null)));

        assertEquals(5, result.size());
        assertArrayEquals(new float[]{0.6f, 0.8f}, result.get(0).values(), 1e-6f);
        assertArrayEquals(new float[]{0f, 1f}, result.get(1).values(), 1e-6f);
        assertArrayEquals(new float[]{1f, 0f}, result.get(4).values(), 1e-6f);
        assertEquals("gemini-embedding-001", result.get(0).model());
        assertEquals(2, result.get(0).dimensions());

        var usage = service.usage();
        assertEquals(3, usage.requests());
        assertEquals(5, usage.texts());
        assertEquals(24, usage.characters());
        assertEquals(3, usage.reportedTokens());
        assertEquals(4, usage.estimatedTokens());
        server.verify();
    }

    @Test
    void estimatesAboutFourCharactersPerToken() {
        assertEquals(0, EmbeddingService.estimateTokens(0));
        assertEquals(1, EmbeddingService.estimateTokens(1));
        assertEquals(25, EmbeddingService.estimateTokens(100));
    }

    @Test
    void rejectsAllZeroVector() {
        assertThrows(EmbeddingException.class, () -> EmbeddingService.normalize(new float[]{0, 0}));
    }
}
