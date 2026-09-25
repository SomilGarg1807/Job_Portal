package com.somil.jobportal.ai;

/**
 * Published after a job or candidate profile is saved, so its embedding can be refreshed
 * in the background. Nothing listens when embedding is disabled, so publishing is free.
 */
public record EmbeddingSourceChanged(EmbeddingTarget target, int id) {
    public static EmbeddingSourceChanged job(int jobPostId) {
        return new EmbeddingSourceChanged(EmbeddingTarget.JOB, jobPostId);
    }

    public static EmbeddingSourceChanged candidate(int userAccountId) {
        return new EmbeddingSourceChanged(EmbeddingTarget.CANDIDATE, userAccountId);
    }
}
