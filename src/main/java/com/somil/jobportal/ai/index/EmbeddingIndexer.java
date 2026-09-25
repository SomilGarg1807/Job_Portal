package com.somil.jobportal.ai.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.ai.EmbeddingTarget;
import com.somil.jobportal.ai.embedding.Embedder;
import com.somil.jobportal.ai.embedding.Embedding;
import com.somil.jobportal.ai.embedding.EmbeddingInput;
import com.somil.jobportal.ai.store.EmbeddingKey;
import com.somil.jobportal.ai.store.EmbeddingStore;
import com.somil.jobportal.ai.store.StoredEmbedding;
import com.somil.jobportal.ai.text.SourceHash;

/**
 * Brings stored vectors up to date for a set of ids. Used by both the on-save queue and the
 * backfill, so both follow the same rules:
 *
 * <ol>
 *   <li>Build the text for every id and strategy.</li>
 *   <li>Skip blank texts, and texts whose hash matches the stored one (no quota spent).</li>
 *   <li>Embed the rest one batch at a time, and store each batch as soon as it returns, so
 *       a quota error part-way through never throws away vectors that were already paid for.</li>
 * </ol>
 */
public class EmbeddingIndexer {
    private final EmbeddingSourceLoader loader;
    private final Embedder embedder;
    private final EmbeddingStore store;

    public EmbeddingIndexer(EmbeddingSourceLoader loader, Embedder embedder, EmbeddingStore store) {
        this.loader = loader;
        this.embedder = embedder;
        this.store = store;
    }

    public IndexResult index(EmbeddingTarget target, List<EmbeddingStrategy> strategies, Collection<Integer> ids,
                             boolean dryRun) {
        if (ids.isEmpty() || strategies.isEmpty()) return IndexResult.NONE;
        List<SourceText> texts = loader.load(target, strategies, ids);
        Map<EmbeddingKey, String> stored = store.hashes(target, ids);

        int empty = 0;
        int unchanged = 0;
        List<SourceText> stale = new ArrayList<>();
        List<String> staleHashes = new ArrayList<>();
        for (SourceText text : texts) {
            if (text.text() == null || text.text().isBlank()) {
                empty++;
                continue;
            }
            String hash = SourceHash.of(text.strategy(), embedder.model(), embedder.dimensions(), text.text());
            if (hash.equals(stored.get(new EmbeddingKey(text.id(), text.strategy().id())))) {
                unchanged++;
            } else {
                stale.add(text);
                staleHashes.add(hash);
            }
        }

        if (!dryRun) {
            for (int start = 0; start < stale.size(); start += embedder.batchSize()) {
                int end = Math.min(start + embedder.batchSize(), stale.size());
                List<SourceText> batch = stale.subList(start, end);
                List<Embedding> vectors = embedder.embed(batch.stream()
                        .map(text -> new EmbeddingInput(text.text(), text.strategy().taskType())).toList());
                List<StoredEmbedding> rows = new ArrayList<>(batch.size());
                for (int i = 0; i < batch.size(); i++) {
                    SourceText text = batch.get(i);
                    Embedding vector = vectors.get(i);
                    rows.add(new StoredEmbedding(text.id(), text.strategy().id(), vector.model(), vector.dimensions(),
                            staleHashes.get(start + i), text.text().length(), vector.values()));
                }
                store.upsert(target, rows);
            }
        }
        return new IndexResult(texts.size(), empty, unchanged, stale.size());
    }
}
