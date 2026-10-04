#!/usr/bin/env python3
"""Validate or apply a completed human review CSV to the draft label JSON."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path


DATA = Path(__file__).resolve().parent / "data"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="apply a fully completed review to labels-draft.json")
    args = parser.parse_args()
    path = DATA / "labels-draft.json"
    labels = json.loads(path.read_text(encoding="utf-8"))
    already_applied = labels.get("explicitReviewCompleted", False)
    expected = {(item["queryId"], key): grade
                for item in labels["queries"] for key, grade in item["judgments"].items()}
    seen: dict[tuple[str, str], int | None] = {}
    with (DATA / "labels-review-341-DRAFT.csv").open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        for row in reader:
            pair = (row["queryId"], row["jobKey"])
            if pair not in expected or pair in seen:
                raise SystemExit(f"Unexpected or duplicate review row: {pair}")
            if not already_applied and str(expected[pair]) != row["draftGrade"]:
                raise SystemExit(f"Stale draft grade in review row: {pair}")
            value = row["reviewedGrade"].strip()
            if value and value not in {"0", "1", "2"}:
                raise SystemExit(f"Reviewed grade must be 0, 1 or 2: {pair}")
            seen[pair] = int(value) if value else None
    if set(seen) != set(expected):
        raise SystemExit(f"Review CSV has {len(seen)} rows; expected {len(expected)} unique judgments.")
    complete = sum(grade is not None for grade in seen.values())
    print(f"Reviewed grades filled: {complete}/{len(expected)}")
    if already_applied:
        if complete != len(expected) or any(seen[pair] != expected[pair] for pair in expected):
            raise SystemExit("Applied JSON no longer matches the reviewed CSV; no labels were changed.")
        print("Review is already applied; no labels were changed.")
        return
    if not args.apply:
        return
    if complete != len(expected):
        raise SystemExit("Review is incomplete; no labels were changed.")
    for item in labels["queries"]:
        item["judgments"] = {key: seen[(item["queryId"], key)] for key in item["judgments"]}
        item["explicitReviewCompleted"] = True
    labels["explicitReviewCompleted"] = True
    labels["notice"] = (
        "Explicit pool reviewed by a human. The file remains DRAFT until missing relevant jobs "
        "and newly retrieved unjudged results have also been reviewed; no aggregate metrics yet."
    )
    path.write_text(json.dumps(labels, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Applied reviewed grades to labels-draft.json; coverage is still pending.")


if __name__ == "__main__":
    main()
