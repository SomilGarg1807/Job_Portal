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
