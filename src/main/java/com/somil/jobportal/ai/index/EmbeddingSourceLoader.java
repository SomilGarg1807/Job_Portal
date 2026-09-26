package com.somil.jobportal.ai.index;

import java.util.Collection;
import java.util.List;

import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.ai.EmbeddingTarget;

/** Reads jobs and profiles and turns them into text. An interface so tests need no database. */
public interface EmbeddingSourceLoader {
    /** Up to {@code limit} ids greater than {@code afterId}, in ascending order (keyset paging). */
    List<Integer> idsAfter(EmbeddingTarget target, int afterId, int limit);

    /** One entry per existing id and strategy. Ids that no longer exist are left out. */
    List<SourceText> load(EmbeddingTarget target, List<EmbeddingStrategy> strategies, Collection<Integer> ids);
}
