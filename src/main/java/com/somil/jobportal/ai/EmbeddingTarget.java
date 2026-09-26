package com.somil.jobportal.ai;

/**
 * The two kinds of thing we embed, with the tables that hold their source rows and
 * their vectors. Table and column names are constants, never user input, so they are
 * safe to place in SQL.
 */
public enum EmbeddingTarget {
    JOB("job_post_activity", "job_post_id", "ai_job_embedding", "job_post_id"),
    CANDIDATE("job_seeker_profile", "user_account_id", "ai_candidate_embedding", "user_account_id");

    private final String sourceTable;
    private final String sourceIdColumn;
    private final String embeddingTable;
    private final String embeddingIdColumn;

    EmbeddingTarget(String sourceTable, String sourceIdColumn, String embeddingTable, String embeddingIdColumn) {
        this.sourceTable = sourceTable;
        this.sourceIdColumn = sourceIdColumn;
        this.embeddingTable = embeddingTable;
        this.embeddingIdColumn = embeddingIdColumn;
    }

    public String sourceTable() { return sourceTable; }
    public String sourceIdColumn() { return sourceIdColumn; }
    public String embeddingTable() { return embeddingTable; }
    public String embeddingIdColumn() { return embeddingIdColumn; }
}
