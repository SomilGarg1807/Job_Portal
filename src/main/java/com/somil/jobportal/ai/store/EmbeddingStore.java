package com.somil.jobportal.ai.store;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.somil.jobportal.ai.EmbeddingTarget;

/** Reads and writes stored vectors. Kept as an interface so tests can use an in-memory copy. */
public interface EmbeddingStore {
    /** Source hash of every stored vector for these ids, across all strategies. */
    Map<EmbeddingKey, String> hashes(EmbeddingTarget target, Collection<Integer> ids);

    /** Inserts new rows and replaces existing ones with the same id and strategy. */
    void upsert(EmbeddingTarget target, List<StoredEmbedding> rows);
}
