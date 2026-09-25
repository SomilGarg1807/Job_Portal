package com.somil.jobportal.ai.backfill;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.ai.EmbeddingTarget;
import com.somil.jobportal.ai.embedding.EmbeddingException;
import com.somil.jobportal.ai.index.EmbeddingIndexer;
import com.somil.jobportal.ai.index.EmbeddingSourceLoader;
import com.somil.jobportal.ai.index.IndexResult;

/**
 * Walks every job or profile in id order, one page (= one Gemini batch) at a time, and
 * brings its vectors up to date.
 *
 * <p>Resumable in two ways: rows whose text is unchanged are skipped for free, so simply
 * running it again continues where it stopped; and {@code fromId} skips ids already done
 * without even reading them. When the quota runs out it stops and prints the id to resume from.
 */
public class BackfillJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackfillJob.class);

    public static final int OK = 0;
    public static final int FAILED = 1;
    public static final int QUOTA_EXHAUSTED = 2;

    /** {@code maxRows} of 0 means no limit. */
    public record Options(EmbeddingTarget target, List<EmbeddingStrategy> strategies, int fromId, int maxRows,
                          int pageSize, boolean dryRun) {
    }

    public record Outcome(int exitCode, IndexResult result, int lastCompletedId) {
    }

    private final EmbeddingSourceLoader loader;
    private final EmbeddingIndexer indexer;

    public BackfillJob(EmbeddingSourceLoader loader, EmbeddingIndexer indexer) {
        this.loader = loader;
        this.indexer = indexer;
    }

    public Outcome run(Options options) {
        IndexResult total = IndexResult.NONE;
        int afterId = options.fromId();
        int rows = 0;
        String strategyIds = options.strategies().stream().map(EmbeddingStrategy::id).toList().toString();
        LOGGER.info("Backfill {} {} from id > {}{}", options.target(), strategyIds, afterId, options.dryRun() ? " (dry run)" : "");
        while (options.maxRows() == 0 || rows < options.maxRows()) {
            int limit = options.maxRows() == 0 ? options.pageSize() : Math.min(options.pageSize(), options.maxRows() - rows);
            List<Integer> ids = loader.idsAfter(options.target(), afterId, limit);
            if (ids.isEmpty()) break;
            try {
                total = total.plus(indexer.index(options.target(), options.strategies(), ids, options.dryRun()));
            } catch (EmbeddingException ex) {
                LOGGER.error("Backfill stopped at {} ids {}..{}: {} Re-run with --ai.backfill.from-id={} to continue "
                        + "(already-embedded rows are skipped either way).", options.target(), ids.get(0),
                        ids.get(ids.size() - 1), ex.getMessage(), afterId);
                return new Outcome(ex.isQuotaExhausted() ? QUOTA_EXHAUSTED : FAILED, total, afterId);
            }
            afterId = ids.get(ids.size() - 1);
            rows += ids.size();
            LOGGER.info("Backfill {} progress: up to id {} ({})", options.target(), afterId, total);
        }
        LOGGER.info("Backfill {} finished: {}", options.target(), total);
        return new Outcome(OK, total, afterId);
    }
}
