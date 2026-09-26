package com.somil.jobportal.ai.index;

/**
 * What one indexing pass did. {@code embedded} is the only number that cost quota; in a
 * dry run it counts what would have been embedded.
 */
public record IndexResult(int considered, int empty, int unchanged, int embedded) {
    public static final IndexResult NONE = new IndexResult(0, 0, 0, 0);

    public IndexResult plus(IndexResult other) {
        return new IndexResult(considered + other.considered, empty + other.empty,
                unchanged + other.unchanged, embedded + other.embedded);
    }

    @Override
    public String toString() {
        return "considered=" + considered + " embedded=" + embedded + " unchanged=" + unchanged + " empty=" + empty;
    }
}
