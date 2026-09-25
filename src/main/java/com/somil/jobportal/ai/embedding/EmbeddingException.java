package com.somil.jobportal.ai.embedding;

/**
 * An embedding call failed. {@link #isQuotaExhausted()} is true when retrying now is
 * pointless (rate limit still hit after every retry, or a daily quota), so callers such as
 * the backfill should stop and resume later rather than keep spending requests.
 *
 * <p>Messages never contain the API key or the provider's response body.
 */
public class EmbeddingException extends RuntimeException {
    private final boolean quotaExhausted;

    public EmbeddingException(String message, boolean quotaExhausted) {
        super(message);
        this.quotaExhausted = quotaExhausted;
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
        this.quotaExhausted = false;
    }

    public boolean isQuotaExhausted() {
        return quotaExhausted;
    }
}
