"""Merge human grades from priority-review.csv into both label files; dry run unless --apply.

The grades replace the agent estimates for those query/job pairs in data/labels-draft.json and in
data/labels-agent-estimate.json (which CI uses), and each file records which pairs a human graded.
Rows left blank are skipped, so the review can be imported in parts.
"""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
LABEL_FILES = [ROOT / "data" / "labels-draft.json", ROOT / "data" / "labels-agent-estimate.json"]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path, help="priority-review.csv downloaded from the review page")
    parser.add_argument("--apply", action="store_true", help="Write the grades into the label files")
    args = parser.parse_args()
    jobs = {job["key"] for job in json.loads((ROOT / "data/jobs.json").read_text(encoding="utf-8"))["jobs"]}
    with args.csv.open(newline="", encoding="utf-8-sig") as source:
        rows = list(csv.DictReader(source))
    graded: dict[tuple[str, str], int] = {}
    for row in rows:
        pair = (row["queryId"], row["jobKey"])
        grade = row["reviewedGrade"].strip()
        if pair[1] not in jobs or pair in graded:
            raise SystemExit(f"Invalid or duplicate query/job pair: {pair[0]} / {pair[1]}. No labels changed.")
        if grade == "":
            continue
        if grade not in {"0", "1", "2"}:
            raise SystemExit(f"Grade must be 0, 1 or 2 for {pair[0]} / {pair[1]}. No labels changed.")
        graded[pair] = int(grade)
    if not graded:
        raise SystemExit("No graded rows found. No labels changed.")

    for path in LABEL_FILES:
        labels = json.loads(path.read_text(encoding="utf-8"))
        queries = {q["queryId"]: q for q in labels["queries"]}
        changed = 0
        for (query_id, key), grade in graded.items():
            if query_id not in queries:
                raise SystemExit(f"Unknown query {query_id} in {path.name}. No labels changed.")
            if queries[query_id]["judgments"].get(key) != grade:
                changed += 1
            queries[query_id]["judgments"][key] = grade
        reviewed = set(labels.get("humanPriorityReviewed", []))
        reviewed.update(f"{query_id}|{key}" for query_id, key in graded)
        labels["humanPriorityReviewed"] = sorted(reviewed)
        print(f"{path.name}: {len(graded)} graded, {changed} differ from the current label"
              + ("" if args.apply else " (dry run)"))
        if args.apply:
            path.write_text(json.dumps(labels, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    skipped = len(rows) - len(graded)
    print(f"{skipped} blank row(s) skipped." if skipped else "All rows graded.")
    if not args.apply:
        print("Run again with --apply to write the grades.")


if __name__ == "__main__":
    main()
