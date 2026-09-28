package com.somil.jobportal.ai.index;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.somil.jobportal.ai.*;

class EmbeddingRecoveryTests {
    @Test void retriesFailedPageThenAdvancesAndWrapsForLaterEdits() {
        var loader = mock(EmbeddingSourceLoader.class);
        var indexer = mock(EmbeddingIndexer.class);
        when(loader.idsAfter(EmbeddingTarget.JOB, 0, 50)).thenReturn(List.of(7));
        when(indexer.index(eq(EmbeddingTarget.JOB), anyList(), eq(List.of(7)), eq(false)))
                .thenThrow(new RuntimeException("temporary failure")).thenReturn(IndexResult.NONE);
        try (var recovery = new EmbeddingRecovery(loader, indexer, EmbeddingUpdateQueueTests.properties(), Duration.ofHours(1))) {
            recovery.recover(); // failed, cursor retained
            recovery.recover(); // succeeds
            recovery.recover(); // end of catalogue, resets cursor
            recovery.recover(); // checks earlier records for changes again
            verify(loader, times(3)).idsAfter(EmbeddingTarget.JOB, 0, 50);
            verify(loader).idsAfter(EmbeddingTarget.JOB, 7, 50);
            verify(indexer, times(3)).index(eq(EmbeddingTarget.JOB), anyList(), eq(List.of(7)), eq(false));
        }
    }
}
