package com.somil.jobportal.ai.index;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
import com.somil.jobportal.ai.*;

/** Reconciles source hashes after failed saves, full queues and process restarts. */
public class EmbeddingRecovery implements AutoCloseable {
    private final EmbeddingSourceLoader loader;
    private final EmbeddingIndexer indexer;
    private final AiEmbeddingProperties properties;
    private final Map<EmbeddingTarget, Integer> cursors = new EnumMap<>(EmbeddingTarget.class);
    private final ScheduledExecutorService worker;

    public EmbeddingRecovery(EmbeddingSourceLoader loader, EmbeddingIndexer indexer,
            AiEmbeddingProperties properties, Duration interval) {
        if (interval.compareTo(Duration.ofSeconds(30)) < 0) throw new IllegalArgumentException("Recovery interval must be at least 30s");
        this.loader = loader;
        this.indexer = indexer;
        this.properties = properties;
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ai-embedding-recovery"); t.setDaemon(true); return t;
        });
        worker.scheduleWithFixedDelay(this::recover, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    void recover() {
        for (EmbeddingTarget target : EmbeddingTarget.values()) {
            try {
                var ids = loader.idsAfter(target, cursors.getOrDefault(target, 0), 50);
                if (ids.isEmpty()) { cursors.put(target, 0); continue; }
                var result = indexer.index(target, properties.strategiesFor(target), ids, false);
                cursors.put(target, ids.get(ids.size() - 1));
                LoggerFactory.getLogger(getClass()).info("Embedding recovery: target={} {}", target, result);
            } catch (RuntimeException ex) {
                LoggerFactory.getLogger(getClass()).warn("Embedding recovery deferred: target={} error={}", target, ex.getClass().getSimpleName());
                // Keep the cursor so this page is retried on the next pass.
            }
        }
    }
    public void close() { worker.shutdownNow(); }
}
