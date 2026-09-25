package com.somil.jobportal.ai.store;

/** One row of ai_job_embedding or ai_candidate_embedding. */
public record StoredEmbedding(int id, String strategy, String model, int dimensions, String sourceHash,
                              int sourceChars, float[] vector) {
}
