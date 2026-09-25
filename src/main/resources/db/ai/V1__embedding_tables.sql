-- Semantic matching: one row per (entity, embedding strategy).
--
-- VECTOR(768) must equal GEMINI_EMBEDDING_DIMENSIONS. A different dimension cannot be
-- stored in this column, so changing it needs a new migration, not a config change.
--
-- There is deliberately no vector index yet. TiDB only uses a vector index for a plain
-- ORDER BY distance LIMIT k query with no WHERE clause, and matching always filters
-- (location, experience, employment type, who may see whom). Exact search over the
-- filtered rows is correct and fast at this scale. See ai/README.md.

CREATE TABLE IF NOT EXISTS ai_job_embedding (
    job_post_id   INT          NOT NULL,
    strategy      VARCHAR(32)  NOT NULL,
    model         VARCHAR(64)  NOT NULL,
    dimensions    SMALLINT     NOT NULL,
    source_hash   CHAR(64)     NOT NULL,
    source_chars  INT          NOT NULL,
    embedding     VECTOR(768)  NOT NULL,
    embedded_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (job_post_id, strategy)
);

CREATE TABLE IF NOT EXISTS ai_candidate_embedding (
    user_account_id INT          NOT NULL,
    strategy        VARCHAR(32)  NOT NULL,
    model           VARCHAR(64)  NOT NULL,
    dimensions      SMALLINT     NOT NULL,
    source_hash     CHAR(64)     NOT NULL,
    source_chars    INT          NOT NULL,
    embedding       VECTOR(768)  NOT NULL,
    embedded_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_account_id, strategy)
);
