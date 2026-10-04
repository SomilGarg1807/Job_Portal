"""Validate human judgments from the Stage 3 result pool; apply only with --apply."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SHEET = ROOT / "results" / "supplemental-review.csv"
LABELS = ROOT / "data" / "labels-draft.json"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Merge completed review grades into draft JSON")
    args = parser.parse_args()
    labels = json.loads(LABELS.read_text(encoding="utf-8"))
    queries = {row["queryId"]: row for row in labels["queries"]}
    jobs = {row["key"] for row in json.loads((ROOT / "data" / "jobs.json").read_text(encoding="utf-8"))["jobs"]}
    with SHEET.open(newline="", encoding="utf-8-sig") as source:
        rows = list(csv.DictReader(source))
    if not rows:
        raise SystemExit("No supplemental rows found. No labels changed.")
    seen: set[tuple[str, str]] = set()
    missing = 0
    for row in rows:
        query_id, job_key, grade = row["queryId"], row["jobKey"], row["reviewedGrade"].strip()
        pair = (query_id, job_key)
        if query_id not in queries or job_key not in jobs or pair in seen:
            raise SystemExit(f"Invalid or duplicate query/job pair: {query_id} / {job_key}")
        seen.add(pair)
        if grade not in {"0", "1", "2"}:
            missing += 1
    if missing:
        raise SystemExit(f"{missing} rows need reviewedGrade 0, 1, or 2. No labels changed.")
    if not args.apply:
        print(f"Validated {len(rows)} supplemental judgments. Run with --apply to merge them.")
        return
    for row in rows:
        queries[row["queryId"]]["judgments"][row["jobKey"]] = int(row["reviewedGrade"])
    agent_count = sum("PROVISIONAL agent" in row.get("reviewNote", "") for row in rows)
    labels["reviewStatus"] = "DRAFT - supplemental result pool graded; recall coverage still unverified"
    labels["agentSupplementalCount"] = agent_count
    labels["notice"] = ("First 341 explicit judgments were human-reviewed. "
                        f"{agent_count} supplemental judgments were agent-estimated, not human ground truth. "
                        "Recall coverage remains unverified.")
    labels["coverageReviewed"] = False
    LABELS.write_text(json.dumps(labels, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Merged {len(rows)} reviewed judgments. Recall coverage remains unverified.")


if __name__ == "__main__":
    main()
