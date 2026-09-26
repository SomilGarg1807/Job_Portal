package com.somil.jobportal.ai.index;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionalEventListener;

import com.somil.jobportal.ai.AiEmbeddingProperties;
import com.somil.jobportal.ai.EmbeddingSourceChanged;
import com.somil.jobportal.ai.EmbeddingTarget;

/**
 * Re-embeds jobs and profiles in the background after they are saved.
 *
 * <p>Saves are collected for a few seconds ({@code save-delay}) and embedded together, so
 * several quick edits cost one Gemini call, and duplicates collapse into one. Work runs on a
 * single background thread and never on the request thread, so saving a job never waits for
 * Gemini and never fails because of it. If embedding fails (say, a 429 after all retries),
 * the vector just stays stale and the next backfill picks it up.
 */
public class EmbeddingUpdateQueue implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingUpdateQueue.class);
    /** More pending saves than this means something is wrong; the backfill can catch up. */
    static final int MAX_PENDING = 1000;

    private final EmbeddingIndexer indexer;
    private final AiEmbeddingProperties properties;
    private final ScheduledExecutorService worker;
    private final Set<EmbeddingSourceChanged> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public EmbeddingUpdateQueue(EmbeddingIndexer indexer, AiEmbeddingProperties properties) {
        this(indexer, properties, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ai-embedding");
            thread.setDaemon(true);
            return thread;
        }));
    }

    EmbeddingUpdateQueue(EmbeddingIndexer indexer, AiEmbeddingProperties properties, ScheduledExecutorService worker) {
        this.indexer = indexer;
        this.properties = properties;
        this.worker = worker;
    }

    /** Runs after the save commits (or straight away when the save had no surrounding transaction). */
    @TransactionalEventListener(fallbackExecution = true)
    public void onSourceChanged(EmbeddingSourceChanged event) {
        if (pending.size() >= MAX_PENDING) {
            LOGGER.warn("Embedding queue is full; {} {} will be embedded by the next backfill", event.target(), event.id());
            return;
        }
        pending.add(event);
        if (scheduled.compareAndSet(false, true)) {
            worker.schedule(this::drain, properties.saveDelay().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    void drain() {
        scheduled.set(false);
        Map<EmbeddingTarget, List<Integer>> byTarget = new EnumMap<>(EmbeddingTarget.class);
        for (EmbeddingSourceChanged event : new ArrayList<>(pending)) {
            pending.remove(event);
            byTarget.computeIfAbsent(event.target(), target -> new ArrayList<>()).add(event.id());
        }
        byTarget.forEach((target, ids) -> {
            try {
                IndexResult result = indexer.index(target, properties.strategiesFor(target), ids, false);
                LOGGER.info("Refreshed embeddings after save: target={} ids={} {}", target, ids.size(), result);
            } catch (RuntimeException ex) {
                LOGGER.warn("Could not refresh embeddings for {} {}: {}. The next backfill will retry.",
                        target, ids, ex.getMessage());
            }
        });
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
