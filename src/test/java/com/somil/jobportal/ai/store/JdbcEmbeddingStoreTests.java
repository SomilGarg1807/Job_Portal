package com.somil.jobportal.ai.store;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.somil.jobportal.ai.EmbeddingTarget;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the store's real SQL on H2 in MySQL mode. H2 has no VECTOR type, so the test table
 * holds the vector's text form in a VARCHAR, which is exactly what the store sends to TiDB.
 */
class JdbcEmbeddingStoreTests {
    private JdbcTemplate jdbc;
    private JdbcEmbeddingStore store;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:store-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", ""));
        jdbc.execute("CREATE TABLE ai_job_embedding (job_post_id INT NOT NULL, strategy VARCHAR(32) NOT NULL,"
                + " model VARCHAR(64) NOT NULL, dimensions SMALLINT NOT NULL, source_hash CHAR(64) NOT NULL,"
                + " source_chars INT NOT NULL, embedding VARCHAR(20000) NOT NULL, embedded_at DATETIME(6) NOT NULL,"
                + " PRIMARY KEY (job_post_id, strategy))");
        store = new JdbcEmbeddingStore(jdbc);
    }

    @Test
    void insertsThenReplacesByIdAndStrategy() {
        store.upsert(EmbeddingTarget.JOB, List.of(
                new StoredEmbedding(1, "job_full_v1", "gemini-embedding-001", 2, "hash-a", 10, new float[]{0.6f, 0.8f}),
                new StoredEmbedding(1, "job_req_v1", "gemini-embedding-001", 2, "hash-b", 5, new float[]{1f, 0f}),
                new StoredEmbedding(2, "job_full_v1", "gemini-embedding-001", 2, "hash-c", 7, new float[]{0f, 1f})));
        store.upsert(EmbeddingTarget.JOB, List.of(
                new StoredEmbedding(1, "job_full_v1", "gemini-embedding-001", 2, "hash-a2", 12, new float[]{0.00001f, -1f})));

        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM ai_job_embedding", Integer.class));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT source_hash, source_chars, embedding, model, dimensions FROM ai_job_embedding WHERE job_post_id = 1 AND strategy = 'job_full_v1'");
        assertEquals("hash-a2", row.get("source_hash"));
        assertEquals(12, ((Number) row.get("source_chars")).intValue());
        assertEquals("[0.00001,-1]", row.get("embedding"));
        assertEquals("gemini-embedding-001", row.get("model"));
        assertEquals(2, ((Number) row.get("dimensions")).intValue());
    }

    @Test
    void readsHashesForRequestedIdsAcrossStrategies() {
        store.upsert(EmbeddingTarget.JOB, List.of(
                new StoredEmbedding(1, "job_full_v1", "m", 2, "hash-a", 1, new float[]{1f, 0f}),
                new StoredEmbedding(1, "job_req_v1", "m", 2, "hash-b", 1, new float[]{1f, 0f}),
                new StoredEmbedding(3, "job_full_v1", "m", 2, "hash-c", 1, new float[]{1f, 0f})));

        assertEquals(Map.of(new EmbeddingKey(1, "job_full_v1"), "hash-a", new EmbeddingKey(1, "job_req_v1"), "hash-b"),
                store.hashes(EmbeddingTarget.JOB, List.of(1, 2)));
        assertTrue(store.hashes(EmbeddingTarget.JOB, List.of()).isEmpty());
    }

    @Test
    void vectorTextUsesPlainDecimals() {
        assertEquals("[0.5,-0.00001,1]", VectorText.format(new float[]{0.5f, -0.00001f, 1f}));
        assertThrows(IllegalArgumentException.class, () -> VectorText.format(new float[]{Float.NaN}));
    }
}
