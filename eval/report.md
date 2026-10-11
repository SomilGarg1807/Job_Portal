# Semantic search evaluation — provisional results

Commit: `7fbaea8e95fbafdd620d62469773b35ed30c1c27` · Model: `gemini-embedding-001` · Dimension: 768 · Jobs: 288 · UTC: 2026-10-11T14:34:20.488682100Z

**Provisional agent estimate.** These labels are not human ground truth; do not treat threshold comparisons as proof.

This is a tracked snapshot of the local eval output. The commit hash above was the base commit; the per-strategy runner change was uncommitted at that time. See `README.md` for examples, limits, and the threshold tradeoff.

`max` is the per-job maximum over both job strategies, which is what production search uses.

| Mode | Strategy | Threshold | P@5 | R@10 | MRR@10 | No-answer accuracy | p50 ms | p95 ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| keyword | none | 0.35 | 0.370 | 0.168 | 0.450 | 1.000 | 904.0 | 957.0 |
| keyword | none | 0.45 | 0.370 | 0.168 | 0.450 | 1.000 | 904.0 | 957.0 |
| keyword | none | 0.55 | 0.370 | 0.168 | 0.450 | 1.000 | 904.0 | 957.0 |
| keyword | none | 0.65 | 0.370 | 0.168 | 0.450 | 1.000 | 904.0 | 957.0 |
| keyword | none | 0.75 | 0.370 | 0.168 | 0.450 | 1.000 | 904.0 | 957.0 |
| semantic | job_full_v1 | 0.35 | 0.780 | 0.488 | 0.913 | 0.000 | 1951.0 | 2091.0 |
| semantic | job_full_v1 | 0.45 | 0.780 | 0.488 | 0.913 | 0.000 | 1951.0 | 2088.0 |
| semantic | job_full_v1 | 0.55 | 0.780 | 0.488 | 0.913 | 0.000 | 1951.0 | 2088.0 |
| semantic | job_full_v1 | 0.65 | 0.780 | 0.488 | 0.913 | 0.000 | 1951.0 | 2088.0 |
| semantic | job_full_v1 | 0.75 | 0.755 | 0.471 | 0.888 | 1.000 | 1951.0 | 2088.0 |
| semantic | job_req_v1 | 0.35 | 0.825 | 0.515 | 0.913 | 0.000 | 1951.0 | 2088.0 |
| semantic | job_req_v1 | 0.45 | 0.825 | 0.515 | 0.913 | 0.000 | 1951.0 | 2088.0 |
| semantic | job_req_v1 | 0.55 | 0.825 | 0.515 | 0.913 | 0.000 | 1951.0 | 2088.0 |
| semantic | job_req_v1 | 0.65 | 0.825 | 0.515 | 0.913 | 0.000 | 1951.0 | 2088.0 |
| semantic | job_req_v1 | 0.75 | 0.800 | 0.494 | 0.913 | 1.000 | 1951.0 | 2088.0 |
| semantic | max | 0.35 | 0.815 | 0.510 | 0.925 | 0.000 | 1951.0 | 2088.0 |
| semantic | max | 0.45 | 0.815 | 0.510 | 0.925 | 0.000 | 1951.0 | 2088.0 |
| semantic | max | 0.55 | 0.815 | 0.510 | 0.925 | 0.000 | 1951.0 | 2088.0 |
| semantic | max | 0.65 | 0.815 | 0.510 | 0.925 | 0.000 | 1951.0 | 2088.0 |
| semantic | max | 0.75 | 0.795 | 0.492 | 0.925 | 1.000 | 1951.0 | 2088.0 |
| hybrid | job_full_v1 | 0.35 | 0.830 | 0.519 | 0.925 | 0.000 | 2861.0 | 3157.0 |
| hybrid | job_full_v1 | 0.45 | 0.830 | 0.519 | 0.925 | 0.000 | 2861.0 | 3157.0 |
| hybrid | job_full_v1 | 0.55 | 0.830 | 0.519 | 0.925 | 0.000 | 2861.0 | 3157.0 |
| hybrid | job_full_v1 | 0.65 | 0.830 | 0.519 | 0.925 | 0.000 | 2861.0 | 3157.0 |
| hybrid | job_full_v1 | 0.75 | 0.830 | 0.519 | 0.925 | 1.000 | 2861.0 | 3157.0 |
| hybrid | job_req_v1 | 0.35 | 0.825 | 0.515 | 0.913 | 0.000 | 2861.0 | 3158.0 |
| hybrid | job_req_v1 | 0.45 | 0.825 | 0.515 | 0.913 | 0.000 | 2861.0 | 3157.0 |
| hybrid | job_req_v1 | 0.55 | 0.825 | 0.515 | 0.913 | 0.000 | 2861.0 | 3157.0 |
| hybrid | job_req_v1 | 0.65 | 0.825 | 0.515 | 0.913 | 0.000 | 2861.0 | 3157.0 |
| hybrid | job_req_v1 | 0.75 | 0.825 | 0.515 | 0.913 | 1.000 | 2861.0 | 3157.0 |
| hybrid | max | 0.35 | 0.830 | 0.519 | 0.925 | 0.000 | 2861.0 | 3157.0 |
| hybrid | max | 0.45 | 0.830 | 0.519 | 0.925 | 0.000 | 2861.0 | 3157.0 |
| hybrid | max | 0.55 | 0.830 | 0.519 | 0.925 | 0.000 | 2861.0 | 3158.0 |
| hybrid | max | 0.65 | 0.830 | 0.519 | 0.925 | 0.000 | 2861.0 | 3157.0 |
| hybrid | max | 0.75 | 0.830 | 0.519 | 0.925 | 1.000 | 2861.0 | 3157.0 |
