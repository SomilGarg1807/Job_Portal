package com.somil.jobportal.ai.index;

import com.somil.jobportal.ai.EmbeddingStrategy;

/** The text a strategy produced for one job or candidate. Blank means nothing to embed. */
public record SourceText(int id, EmbeddingStrategy strategy, String text) {
}
