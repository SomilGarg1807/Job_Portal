package com.somil.jobportal.ai.index;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.ai.EmbeddingTarget;
import com.somil.jobportal.ai.embedding.EmbeddingException;
import com.somil.jobportal.ai.store.EmbeddingKey;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingIndexerTests {
    private static final List<EmbeddingStrategy> BOTH = List.of(EmbeddingStrategy.JOB_FULL_V1, EmbeddingStrategy.JOB_REQUIREMENTS_V1);

    private Fakes.Loader loader;
    private Fakes.RecordingEmbedder embedder;
    private Fakes.Store store;
    private EmbeddingIndexer indexer;

    @BeforeEach
    void setup() {
        loader = new Fakes.Loader();
        embedder = new Fakes.RecordingEmbedder(2);
        store = new Fakes.Store();
        indexer = new EmbeddingIndexer(loader, embedder, store);
        loader.texts.put(1, "java");
        loader.texts.put(2, "python");
        loader.texts.put(3, " ");
    }

    @Test
    void embedsEveryStrategyAndRecordsModelDimensionAndHash() {
        IndexResult result = indexer.index(EmbeddingTarget.JOB, BOTH, List.of(1, 2, 3), false);

        assertEquals(new IndexResult(6, 2, 0, 4), result);
        assertEquals(2, embedder.calls.size(), "4 texts in batches of 2");
        assertEquals(4, store.rows.size());
        var row = store.rows.get(new EmbeddingKey(1, "job_req_v1"));
        assertEquals("fake-model", row.model());
        assertEquals(2, row.dimensions());
        assertEquals("job_req_v1:java".length(), row.sourceChars());
        assertEquals(64, row.sourceHash().length());
    }

    @Test
    void unchangedTextIsNotReEmbedded() {
        indexer.index(EmbeddingTarget.JOB, BOTH, List.of(1, 2), false);
        embedder.calls.clear();

        loader.texts.put(2, "python and go");
        IndexResult second = indexer.index(EmbeddingTarget.JOB, BOTH, List.of(1, 2), false);

        assertEquals(new IndexResult(4, 0, 2, 2), second);
        assertEquals(List.of(List.of("job_full_v1:python and go", "job_req_v1:python and go")), embedder.calls);
    }

    @Test
    void dryRunCountsWithoutCallingGeminiOrWriting() {
        IndexResult result = indexer.index(EmbeddingTarget.JOB, BOTH, List.of(1, 2), true);

        assertEquals(4, result.embedded());
        assertTrue(embedder.calls.isEmpty());
        assertTrue(store.rows.isEmpty());
    }

    @Test
    void batchesStoredBeforeAQuotaErrorAreKept() {
        embedder.failOnCall = 1;

        assertThrows(EmbeddingException.class, () -> indexer.index(EmbeddingTarget.JOB, BOTH, List.of(1, 2), false));

        assertEquals(2, store.rows.size(), "the first batch was paid for, so it must be stored");
        assertEquals(1, store.upserts);
    }

    @Test
    void nothingToDoForNoIdsOrNoStrategies() {
        assertEquals(IndexResult.NONE, indexer.index(EmbeddingTarget.JOB, BOTH, List.of(), false));
        assertEquals(IndexResult.NONE, indexer.index(EmbeddingTarget.JOB, List.of(), List.of(1), false));
    }
}
