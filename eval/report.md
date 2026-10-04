# Semantic search evaluation — provisional results

Commit: `0d01bbcadff37c7868dbe8ec794871c6bc5d84ab` · Model: `gemini-embedding-001` · Dimension: 768 · Jobs: 288 · UTC: 2026-10-04T16:56:36.149312400Z

**Provisional agent estimate.** These labels are not human ground truth; do not treat threshold comparisons as proof.

This is a tracked snapshot of the local eval output. The commit hash above was the repository's base commit when the run happened; the eval harness and labels were uncommitted at that time. See `README.md` for examples, limits, and the threshold tradeoff.

| Mode | Threshold | P@5 | R@10 | MRR@10 | No-answer accuracy | p50 ms | p95 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| keyword | 0.35 | 0.370 | 0.168 | 0.450 | 1.000 | 914.0 | 989.0 |
| keyword | 0.45 | 0.370 | 0.168 | 0.450 | 1.000 | 914.0 | 989.0 |
| keyword | 0.55 | 0.370 | 0.168 | 0.450 | 1.000 | 914.0 | 989.0 |
| keyword | 0.65 | 0.370 | 0.168 | 0.450 | 1.000 | 914.0 | 989.0 |
| keyword | 0.75 | 0.370 | 0.168 | 0.450 | 1.000 | 914.0 | 989.0 |
| semantic | 0.35 | 0.815 | 0.510 | 0.925 | 0.000 | 2149.0 | 2291.0 |
| semantic | 0.45 | 0.815 | 0.510 | 0.925 | 0.000 | 2149.0 | 2291.0 |
| semantic | 0.55 | 0.815 | 0.510 | 0.925 | 0.000 | 2149.0 | 2291.0 |
| semantic | 0.65 | 0.815 | 0.510 | 0.925 | 0.000 | 2149.0 | 2292.0 |
| semantic | 0.75 | 0.795 | 0.492 | 0.925 | 1.000 | 2149.0 | 2291.0 |
| hybrid | 0.35 | 0.830 | 0.519 | 0.925 | 0.000 | 3067.0 | 3313.0 |
| hybrid | 0.45 | 0.830 | 0.519 | 0.925 | 0.000 | 3067.0 | 3313.0 |
| hybrid | 0.55 | 0.830 | 0.519 | 0.925 | 0.000 | 3067.0 | 3313.0 |
| hybrid | 0.65 | 0.830 | 0.519 | 0.925 | 0.000 | 3067.0 | 3313.0 |
| hybrid | 0.75 | 0.830 | 0.519 | 0.925 | 1.000 | 3067.0 | 3313.0 |
