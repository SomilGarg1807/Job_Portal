package com.somil.jobportal.ai.index;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.somil.jobportal.ai.AiEmbeddingProperties;
import com.somil.jobportal.ai.EmbeddingSourceChanged;
import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.ai.EmbeddingTarget;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmbeddingUpdateQueueTests {
    static AiEmbeddingProperties properties() {
        return new AiEmbeddingProperties(true, "key", "gemini-embedding-001", 768, 50, 5, Duration.ofSeconds(2),
                Duration.ofSeconds(60), Duration.ofSeconds(30), 6000, Duration.ofSeconds(3),
                List.of("job_full_v1"), List.of("cand_profile_v1"));
    }

    @Test
    void collectsSavesAndEmbedsThemTogetherOnce() {
        EmbeddingIndexer indexer = mock(EmbeddingIndexer.class);
        when(indexer.index(any(), any(), any(), anyBoolean())).thenReturn(IndexResult.NONE);
        ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
        var queue = new EmbeddingUpdateQueue(indexer, properties(), worker);

        queue.onSourceChanged(EmbeddingSourceChanged.job(7));
        queue.onSourceChanged(EmbeddingSourceChanged.job(7));
        queue.onSourceChanged(EmbeddingSourceChanged.job(8));
        queue.onSourceChanged(EmbeddingSourceChanged.candidate(3));

        ArgumentCaptor<Runnable> drain = ArgumentCaptor.forClass(Runnable.class);
        verify(worker, times(1)).schedule(drain.capture(), eq(3000L), eq(TimeUnit.MILLISECONDS));
        verifyNoInteractions(indexer);

        drain.getValue().run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Integer>> ids = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(indexer).index(eq(EmbeddingTarget.JOB), eq(List.of(EmbeddingStrategy.JOB_FULL_V1)), ids.capture(), eq(false));
        assertEquals(java.util.Set.of(7, 8), new java.util.HashSet<>(ids.getValue()));
        verify(indexer).index(EmbeddingTarget.CANDIDATE, List.of(EmbeddingStrategy.CANDIDATE_PROFILE_V1), List.of(3), false);

        queue.onSourceChanged(EmbeddingSourceChanged.job(9));
        verify(worker, times(2)).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void embeddingFailureNeverEscapesTheWorker() {
        EmbeddingIndexer indexer = mock(EmbeddingIndexer.class);
        when(indexer.index(any(), any(), any(), anyBoolean())).thenThrow(new RuntimeException("429"));
        var queue = new EmbeddingUpdateQueue(indexer, properties(), mock(ScheduledExecutorService.class));

        queue.onSourceChanged(EmbeddingSourceChanged.job(1));
        assertDoesNotThrow(queue::drain);
    }
}
