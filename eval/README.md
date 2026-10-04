# Semantic job-search evaluation — Stage 2 data

**Labels remain DRAFT, not ground truth.** The human review of the first 341 explicit judgments is recorded, but new retrieved results and recall coverage still need review before any metric or threshold recommendation is trusted. No evaluation scores have been calculated.

The dataset lives here; the Stage 3 runner lives in `src/main/java/com/somil/jobportal/eval`. The runner has its own Spring context and `eval` profile. It never scans or starts the production application and has no fallback to `DB_URL`.

## Files

| File | Purpose |
| --- | --- |
| `generate_dataset.py` | Deterministically generates synthetic jobs and first-pass draft labels. |
| `data/jobs.json` | 288 fictional postings across 12 role families, with stable job keys. |
| `data/queries.json` | 40 hand-written queries with optional structured filters. |
| `data/labels-draft.json` | Canonical **DRAFT** initial judgment pool keyed by query ID and job key. |
| `data/labels-review-341-DRAFT.csv` | Human-reviewed first-pass sheet with context and a `reviewedGrade` for each explicit judgment. |
| `export_review.py` | Regenerates the review CSV from JSON; refuses to overwrite an existing sheet unless `--force` is given. |
| `import_review.py` | Checks review progress and applies a fully completed sheet to the JSON. |
| `import_supplemental.py` | Validates and imports the additional result-pool judgments produced by the runner. |
| `agent_review.py` | Reproducibly drafts supplemental and catalogue-wide agent estimates; never marks them as human ground truth. |
| `data/labels-review-supplemental-agent-DRAFT.csv` | Archived 339 provisional agent grades from the first result pool. |
| `report.md` | Tracked snapshot of the provisional results table; the generated `results/` files remain local. |

The older `data/labels-review-DRAFT.csv` with 1,149 rows is **superseded** and ignored by Git. Use only the `341`-row sheet named above.

The fixed seed is `20261004`. Each of the 12 families has 24 jobs: Java backend, React frontend, data engineering, data analytics, DevOps/SRE, QA automation, Android, iOS, security, product management, UX design, and cloud architecture. Roles vary by title, skills, responsibility, location, experience, employment type, work mode, and posted date. Company names begin with `Synthetic`. There are no real users, resumes, contact details, or copied job listings.

The query mix is eight exact titles, seven synonyms or abbreviations, seven skill-only queries, six role-plus-location queries, five queries with experience or work filters, four natural-language queries, and three intended no-answer queries. `filters` map to the app's separate location, experience, employment-type, and work-mode controls. Skills-only query words are absent from job titles by design.

## Regenerate and validate

From the repository root:

```text
python eval/generate_dataset.py --check
python eval/import_review.py
```

To regenerate the job fixture after changing the generator, run `python eval/generate_dataset.py`. That command **does not overwrite an existing labels file**. `--refresh-draft-labels` does overwrite it and will discard human edits. The review CSV is already included; running `python eval/export_review.py --force` also discards any grades entered in that sheet.

## Human label review

Grades are `0` = irrelevant or excluded by a structured filter, `1` = relevant, and `2` = highly relevant. The **341 explicit judgments** form a focused first review pool: at most four strong candidates, two partial candidates, and four negatives per query (eight negatives for an intended no-answer query). They were drafted from role families and skills, so they still need human judgment about the actual text.

1. Open `data/queries.json` and check all 40 query intents and structured filters first.
2. Open **`data/labels-review-341-DRAFT.csv`** in a spreadsheet and filter by `queryId`. For each row, inspect the job title, location, experience, type, work mode, skills, and description. Enter `0`, `1`, or `2` in **`reviewedGrade`**, even when the draft grade is correct. `reviewNote` is optional. Save as CSV UTF-8.
3. Run `python eval/import_review.py` to see progress. Once all 341 entries have a reviewed grade, run `python eval/import_review.py --apply`, then `python eval/generate_dataset.py --check`. The importer updates the JSON but leaves its overall status **DRAFT** because coverage is not yet established.
4. Look for missing relevant jobs, especially near-duplicate roles in another family. Add their job keys and grades to the JSON. For the three no-answer queries, confirm that no job in the catalogue is actually suitable.

**Unlisted jobs are unjudged, not assumed irrelevant.** In Stage 3, the harness will first collect the unique top results from keyword, semantic, and hybrid modes and produce a supplemental review queue. It must withhold aggregate metrics until every scored result is judged. Full recall@10 also needs a credible review of potentially relevant jobs outside the retrieved pool. If that review is not completed, we must stop and ask before substituting *pooled recall*; we cannot silently claim complete recall. This is why reviewing 341 initial rows does not by itself finish relevance labeling. The synthetic catalogue, 40 queries, and one human labeler remain limitations even after review. If a prompt, scoring rule, or threshold changes after looking at results, the final report must disclose that tuning rather than present the same results as independent proof.

## Stage 3 harness

The CLI uses the production job embedding text builders, the production exact TiDB vector query, and the same hybrid `+1` keyword bonus. The eval-only `eval_job` table mirrors the production keyword `LIKE` query and is populated from `data/jobs.json`. It uses job IDs starting at 900001. Before any SQL or seeding, the CLI requires `EVAL_DB_URL` to name a database containing `eval`; after connecting it confirms `SELECT DATABASE()` matches that exact name and checks `ai_job_embedding.embedding` is `VECTOR(768)`. Production search behavior is unchanged.

Set the following in the ignored repository-root `.env.eval` file, or in environment variables. Do not share its values or commit it:

| Variable | Purpose |
| --- | --- |
| `EVAL_DB_URL` | JDBC MySQL URL for the separate TiDB database, whose name contains `eval`. |
| `EVAL_DB_USERNAME` | Eval database username. |
| `EVAL_DB_PASSWORD` | Eval database password. |
| `GEMINI_API_KEY` | Gemini key for the synthetic job and query embeddings. |
| `EVAL_EMBEDDING_MODEL` | Optional; currently fixed to `gemini-embedding-001`. |
| `EVAL_EMBEDDING_DIMENSIONS` | Optional; currently fixed to `768`. |
| `EVAL_EMBEDDING_BATCH_SIZE` | Optional; defaults to `50`, accepted range 1–100. |

Run from the repository root with the human-review draft labels (quality metrics remain withheld until coverage is human-checked):

```powershell
./mvnw.cmd '-Dspring-boot.run.main-class=com.somil.jobportal.eval.EvalApplication' '-Dspring-boot.run.arguments=--spring.profiles.active=eval' '-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false' spring-boot:run
```

For the explicitly provisional agent-estimated label set used in the exploratory run below, add JVM arguments:

```powershell
./mvnw.cmd '-Dspring-boot.run.main-class=com.somil.jobportal.eval.EvalApplication' '-Dspring-boot.run.arguments=--spring.profiles.active=eval' '-Dspring-boot.run.jvmArguments=-Deval.useAgentLabels=true -Dspring.devtools.restart.enabled=false' spring-boot:run
```

The agent labels are in `data/labels-agent-estimate.json`; the original 341 human-reviewed labels remain in `data/labels-draft.json`. The latter also contains 339 subsequent agent-estimated result-pool grades with clear provenance. The agent estimate uses role-family and skill rules calibrated against the initial human grades, so it is not an independent ground truth set. Disable `eval.useAgentLabels` to return to the default label file.

The CLI writes `eval/results/stage3-results.md`, `stage3-results.json`, `stage3-results.csv`, and `supplemental-review.csv`. The header records commit, timestamp, model, dimension, dataset size, and query count. Each query/mode/threshold row records top 10 keys, latency, and actual Gemini HTTP attempts. It sweeps 0.35, 0.45, 0.55, 0.65, and 0.75 for keyword, semantic, and hybrid modes, with p50/p95 latency per mode and threshold. Gemini embeds missing job texts in batches, retries 429 with backoff, and caches every successful embedding under `eval/cache/<model>/<dimension>/<SHA-256(text)>.json`. A rerun with unchanged inputs can avoid all Gemini calls. The summary prints calls made and calls avoided by cache; individual results record calls made.

Job and query vectors are embedded in batches to protect free-tier quota. `geminiBatchParticipation` is 1 when that query needed a vector in the shared batch; it is not an additional API call for every row. `geminiCalls` records HTTP attempts during that mode run, and the header records actual total calls. For each query, the runner measures keyword retrieval, eligible-job retrieval, and vector matching once, then sweeps thresholds in memory. Each mode's `latencyMs` is the measured time of its required retrieval components plus its own ranking time; `retrievalMs` and `rankingMs` are also recorded. These are warm search estimates, not repeated end-to-end HTTP timings. The one-time query embedding batch cost is reported through API calls and is not attributed to one mode.

Results remain **WITHHELD** while the review queue contains unjudged results or recall coverage has not been checked. Review every row in `eval/results/supplemental-review.csv`, fill `reviewedGrade`, and run `python eval/import_supplemental.py` followed by `python eval/import_supplemental.py --apply`. Then inspect the full catalogue for additional relevant jobs outside the result pool and add those judgments to `labels-draft.json`. Only after you have checked that coverage yourself should you set top-level `coverageReviewed` to `true`. The harness then reports precision@5, recall@10, MRR@10, no-answer accuracy, and p50/p95 latency. Treat these results as evidence from a synthetic dataset and one labeler, not as a general claim about live users.

The JUnit integration test uses real TiDB vector SQL. `./mvnw.cmd '-Dtest=EvalHarnessTests' test` skips the TiDB portion cleanly if the eval database is not configured. It never inserts data.

## Stage 4 exploratory result — 2026-10-04

The full run completed against the separate TiDB eval database: 288 synthetic jobs, 576 job vectors, 40 queries, Gemini `gemini-embedding-001` at 768 dimensions. The guarded integration test confirmed exactly 288 eval jobs and 576 job embeddings. The final cached run made **0 Gemini API calls** and avoided 41 calls through the cache. The generated files in `results/` are local and Git-ignored; `report.md` is a tracked snapshot of their Markdown table. The result header records base commit `0d01bbcadff37c7868dbe8ec794871c6bc5d84ab`, when the eval files were still uncommitted.

**These metrics are provisional agent estimates, not a human-validated benchmark.** The first 341 grades were human-reviewed; the next 339 result-pool grades and catalogue-wide coverage were agent-estimated. The synthetic catalogue, 40 queries, three no-answer cases, and a single initial human labeler all limit the conclusion. Threshold selection below was made after seeing this same evaluation set; it is exploratory tuning, not independent proof.

| Mode | Threshold | Precision@5 | Recall@10 | MRR@10 | No-answer accuracy | p50 / p95 warm search latency |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Keyword | any | 0.370 | 0.168 | 0.450 | 1.000 | 914 / 989 ms |
| Semantic | 0.55 | 0.815 | 0.510 | 0.925 | 0.000 | 2149 / 2291 ms |
| Hybrid | 0.55 | 0.830 | 0.519 | 0.925 | 0.000 | 3067 / 3313 ms |
| Semantic | 0.75 | 0.795 | 0.492 | 0.925 | 1.000 | 2149 / 2291 ms |
| Hybrid | 0.75 | 0.830 | 0.519 | 0.925 | 1.000 | 3067 / 3313 ms |

Hybrid ranked best overall under the provisional labels. Keyword search missed many synonym and natural-language phrases because it requires the whole phrase as a substring. Semantic-only sometimes ranked a related but wrong role above an exact keyword match; hybrid kept the keyword matches. Warm search latency was about three times keyword-only for hybrid because it performs keyword and exact vector retrieval. These are measured retrieval-component plus ranking times, not full HTTP endpoint measurements.

The default `0.55` allowed all three intended no-answer queries to return unrelated jobs. Their maximum cosine similarities were `0.7439` (marine biologist matched an analytics job), `0.7319` (airline pilot matched SRE), and `0.7387` (veterinary surgeon matched cloud engineering). In this sweep, `0.75` was the first tested threshold that returned nothing for all three. Its smallest margin above those maxima is only `0.0061`, and there are just three such queries. **Do not change the production default to 0.75 on this evidence alone.** A human label review and a fresh holdout with more no-answer queries are needed before claiming a justified production threshold.

The higher threshold also has a cost: semantic-only `q16` (Kafka) fell from recall@10 `0.556` at `0.55` to `0.056` at `0.75`; `q21` (Snowflake) fell from `0.444` to `0.222`. Hybrid retained the keyword hits for those queries, so its aggregate precision@5 and recall@10 did not change in this dataset.

Three clear wins at the existing 0.55 threshold **in the 288-job synthetic eval catalogue**. These keyword-only counts were not measured on production; the current production page already uses hybrid search when semantic matching is enabled:

| Query | Keyword result | Semantic or hybrid result |
| --- | --- | --- |
| `q09` SDE working on Java services | No results; P@5 `0.0` | Hybrid top three: Software Development Engineer (Java), all grade 2; P@5 `1.0`. |
| `q12` site reliability and production operations | No results; P@5 `0.0` | Hybrid top three: Platform Reliability Engineer, Site Reliability Engineer, Site Reliability Engineer, all grade 2; P@5 `1.0`. |
| `q34` keep cloud services reliable and respond to incidents | No results; P@5 `0.0` | Hybrid top three: Platform Reliability Engineer, Platform Reliability Engineer, Cloud Operations Engineer, all grade 2; P@5 `1.0`. |

Three cases without a hybrid gain:

| Query | Actual results |
| --- | --- |
| `q16` Kafka | Keyword top three included Data Platform Engineer, ETL Developer, Data Engineer; all grade 2. Keyword and hybrid P@5 were both `1.0`. |
| `q17` Terraform | Keyword top three included Site Reliability Engineer, DevOps Engineer, Infrastructure Automation Engineer; all grade 2. Keyword and hybrid P@5 were both `1.0`. |
| `q22` OAuth | Keyword top three included Cloud Security Engineer, SOC Analyst, Software Development Engineer (Java), all grade 2. Keyword and hybrid P@5 were `1.0`; semantic-only fell to `0.6`. |

The unexpectedly high no-answer cosine scores show why the original 0.55 should not be treated as a calibrated cutoff. The exact TiDB scan is fine for this 288-job fixture, but warm semantic retrieval still costs about 2.1 seconds p50; the current query is not using a vector index. If the synthetic wording, label rules, prompt, or threshold is changed after this run, record that change and evaluate on a new holdout before making a production claim.
