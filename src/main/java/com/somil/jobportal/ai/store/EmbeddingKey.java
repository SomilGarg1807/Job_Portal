package com.somil.jobportal.ai.store;

/** Identifies one stored vector: an entity id and the strategy that produced it. */
public record EmbeddingKey(int id, String strategy) {
}
