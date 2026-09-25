# Semantic matching (`com.somil.jobportal.ai`)

Finds good job ↔ candidate matches by comparing **embeddings** (lists of numbers that
capture meaning) stored in TiDB, instead of sending every pair to Gemini. Gemini is
still used, but only to embed text when something changes, and (from Stage 3) to explain
a match the user actually opens.

Status: **Stage 2**: tables, embedding, backfill and save hooks. Search comes in Stage 3.

## How it works

```
job / profile saved ──► EmbeddingSourceChanged event
                              │ (after the save commits)
                              ▼
                   EmbeddingUpdateQueue  ── waits 3 s, collects saves, one background thread
                              │
backfill CLI ──────────►  EmbeddingIndexer
                              │ 1. EmbeddingTexts builds the text for each strategy
                              │ 2. SourceHash: skip if the text + model + dimension is unchanged
                              │ 3. EmbeddingService → Gemini batchEmbedContents (batched, retried)
                              ▼ 4. JdbcEmbeddingStore upserts ai_job_embedding / ai_candidate_embedding
```

| Package | What is in it |
|---|---|
| `ai` | `EmbeddingStrategy`, `EmbeddingTarget`, the save event, settings, Spring wiring |
| `ai.embedding` | Gemini client (retry/backoff), `EmbeddingService` (batching, logging, usage totals) |
| `ai.text` | The exact text sent for each strategy, and the stale-detection hash |
| `ai.store` | Native SQL for the vector tables (Hibernate has no VECTOR type) |
| `ai.index` | `EmbeddingIndexer` (the shared rules) and the on-save queue |
| `ai.schema` | Tiny versioned migration runner for `src/main/resources/db/ai/V*__*.sql` |
| `ai.backfill` | The command-line backfill |

## Decisions

- **Model:** `gemini-embedding-001` at **768** dimensions (`outputDimensionality` is sent on
  every request). The column is `VECTOR(768)`, and startup fails if the setting differs.
  Vectors are scaled to unit length before storing.
- **Separate tables, native SQL.** One row per `(id, strategy)`, holding `model`,
  `dimensions`, `source_hash` (SHA-256 of strategy + task type + model + dimension + text),
  `source_chars` and `embedded_at`. Changing any input of the hash marks the row stale.
- **No vector index yet.** TiDB only uses one for a plain `ORDER BY VEC_COSINE_DISTANCE(...) LIMIT k`
  with no `WHERE`, and matching always filters. Exact search over the filtered rows is
  correct and fast at this scale, and needs no TiFlash replica.
- **Own migration runner, not Flyway.** Flyway's bootstrap SQL does not run on TiDB (see
  `ProfileSchemaInitializer`). Applied versions are recorded in `ai_schema_version`. Never
  edit an applied script; add `V2__...sql`. Skipped on H2, which the tests use.
- **Never on the search path.** Embedding happens after a save (in the background, so a
  save never waits for or fails because of Gemini) and in the backfill.
- **Off by default.** Nothing here exists unless `AI_EMBEDDING_ENABLED=true`.

## What text is embedded

| Strategy | Text |
|---|---|
| `job_full_v1` | Title, employment type, work mode, experience, full plain-text description |
| `job_req_v1` | Title, experience, and only the requirement-like sections of the description |
| `cand_profile_v1` | Headline, desired role, total experience, employment type, work mode, skills |

Never embedded: location, salary and company (these become SQL filters), or a candidate's
name, contact details, pay, work authorization or resume. Resume text needs a consent
option first. Changing how a strategy builds text needs a new id (e.g. `job_full_v2`).

## Settings

| Variable | Default | Meaning |
|---|---|---|
| `AI_EMBEDDING_ENABLED` | `false` | Master switch (the backfill profile turns it on) |
| `GEMINI_API_KEY` | (none) | Already used by the AI assistant |
| `GEMINI_EMBEDDING_MODEL` | `gemini-embedding-001` | Embedding model |
| `GEMINI_EMBEDDING_DIMENSIONS` | `768` | Must match the `VECTOR(768)` columns |
| `AI_EMBEDDING_BATCH_SIZE` | `50` | Texts per Gemini call (max 100) |
| `AI_EMBEDDING_MAX_RETRIES` | `5` | Retries on 429 / 500 / 503 |
| `AI_EMBEDDING_BACKOFF_MS` | `2000` | First retry delay; doubles each time, capped at 60 s. Gemini's own `retryDelay` wins when given |
| `AI_JOB_STRATEGIES` | `job_full_v1,job_req_v1` | Strategies written for jobs |
| `AI_CANDIDATE_STRATEGIES` | `cand_profile_v1` | Strategies written for candidates |

A 429 that names a per-day quota is not retried. Waiting seconds won't help, and each retry
would spend another request.

## Running the backfill (from your laptop)

Uses the database settings in your `application-local.properties`. No web server starts.

```powershell
# 1. See what would be embedded, without calling Gemini (creates the tables if missing)
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=backfill" "-Dspring-boot.run.arguments=--ai.backfill.dry-run=true"

# 2. Embed for real
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=backfill"
```

Options (combine them in `-Dspring-boot.run.arguments`, comma-separated):
`--ai.backfill.target=all|jobs|candidates`, `--ai.backfill.strategies=job_full_v1`,
`--ai.backfill.from-id=0`, `--ai.backfill.max-rows=0` (0 = all), `--ai.backfill.dry-run=true`.

It is **resumable**: rows whose hash is unchanged are skipped for free, so running it again
continues where it stopped. If the quota runs out it stops, logs the `--ai.backfill.from-id`
to resume from, and exits with code 2.

## Cost visibility

Every Gemini call logs one line:

```
Embedding batch: model=gemini-embedding-001 dims=768 texts=50 chars=61234 tokens=15309 (estimated) latencyMs=812 attempts=1
```

`tokens` is Gemini's own count when the response includes one (`reported`), otherwise
characters ÷ 4 (`estimated`). The backfill ends with a total:
`Gemini usage for this run: requests=… texts=… chars=… tokens(reported)=… tokens(estimated)=…`.

## Still to confirm against the live API

These couldn't be confirmed from the docs site during design. The code checks each one and
fails loudly rather than storing bad data:

- the response has exactly 768 values per text (checked on every vector);
- whether `batchEmbedContents` reports token usage (handled either way);
- the batch limit of 100 texts per call (the default batch size is 50).
