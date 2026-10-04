"""Produce explicitly provisional agent judgments; never marks them as human ground truth."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

from generate_dataset import DRAFT_INTENT, draft_grade, eligible

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"
SHEET = ROOT / "results" / "supplemental-review.csv"


def estimate(job: dict, query: dict) -> int:
    if not eligible(job, query["filters"]):
        return 0
    grade = draft_grade(job, query, DRAFT_INTENT[query["id"]])
    grade = 0 if grade is None else grade
    qid, family = query["id"], job["family"]
    # The initial human sheet consistently rated same-family title variants highly.
    if query["category"] == "exact_title" and family in DRAFT_INTENT[qid]["primary"]:
        grade = 1 if qid == "q05" and job["title"] == "Manual QA Analyst" else 2
    # Calibrate edge cases against the initial human sheet; these remain agent estimates.
    if qid == "q18" and family == "frontend_react" and "Playwright" in job["skills"]:
        grade = 1
    if qid == "q20" and family == "mobile_android" and "Coroutines" not in job["skills"]:
        grade = 1
    if qid == "q29" and family == "qa_automation" and "Java" in job["skills"]:
        grade = 1
    if qid == "q34" and family == "cloud_architecture":
        grade = 1
    return grade


def load() -> tuple[dict, dict, dict]:
    jobs = {job["key"]: job for job in json.loads((DATA / "jobs.json").read_text(encoding="utf-8"))["jobs"]}
    queries = {query["id"]: query for query in json.loads((DATA / "queries.json").read_text(encoding="utf-8"))["queries"]}
    labels = json.loads((DATA / "labels-draft.json").read_text(encoding="utf-8"))
    return jobs, queries, labels


def fill_sheet(jobs: dict, queries: dict) -> None:
    with SHEET.open(newline="", encoding="utf-8-sig") as source:
        rows = list(csv.DictReader(source))
    if not rows:
        raise SystemExit("No supplemental review rows found.")
    for row in rows:
        if row["reviewedGrade"].strip():
            continue
        row["reviewedGrade"] = str(estimate(jobs[row["jobKey"]], queries[row["queryId"]]))
        row["reviewNote"] = "PROVISIONAL agent review; not human ground truth"
    with SHEET.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    print(f"Filled {len(rows)} supplemental rows with provisional agent grades.")


def coverage(jobs: dict, queries: dict, labels: dict) -> None:
    for row in labels["queries"]:
        query = queries[row["queryId"]]
        original = row["judgments"]
        row["judgments"] = {key: original.get(key, estimate(job, query)) for key, job in jobs.items()}
        row["reviewStatus"] = "PROVISIONAL AGENT COVERAGE ESTIMATE"
        row["agentEstimatedCount"] = len(jobs) - len(original)
    labels["reviewStatus"] = "PROVISIONAL AGENT ESTIMATE - NOT HUMAN GROUND TRUTH"
    labels["notice"] = ("The first 341 human-reviewed grades and any imported supplemental grades are preserved. "
                        "Remaining pairs use deterministic agent rules. This is exploratory, not independent ground truth.")
    labels["coverageReviewed"] = True
    labels["coverageReviewedBy"] = "agent"
    (DATA / "labels-agent-estimate.json").write_text(json.dumps(labels, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote provisional coverage estimates for {len(queries)} queries x {len(jobs)} jobs.")


def archive_supplemental(jobs: dict, queries: dict, labels: dict) -> None:
    with (DATA / "labels-review-341-DRAFT.csv").open(newline="", encoding="utf-8-sig") as source:
        initial = {(row["queryId"], row["jobKey"]) for row in csv.DictReader(source)}
    archive = DATA / "labels-review-supplemental-agent-DRAFT.csv"
    rows = []
    for label in labels["queries"]:
        qid = label["queryId"]
        for key, grade in label["judgments"].items():
            if qid in queries and key in jobs and (qid, key) not in initial:
                job = jobs[key]
                rows.append({"queryId": qid, "queryText": queries[qid]["text"], "jobKey": key,
                             "title": job["title"], "city": job["location"]["city"],
                             "jobType": job["jobType"], "remote": job["remote"],
                             "reviewedGrade": grade,
                             "reviewNote": "PROVISIONAL agent review; not human ground truth"})
    with archive.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=["queryId", "queryText", "jobKey", "title", "city",
                                                     "jobType", "remote", "reviewedGrade", "reviewNote"])
        writer.writeheader()
        writer.writerows(sorted(rows, key=lambda row: (row["queryId"], row["jobKey"])))
    print(f"Archived {len(rows)} provisional supplemental grades at {archive.name}.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fill-sheet", action="store_true")
    parser.add_argument("--coverage", action="store_true")
    parser.add_argument("--archive", action="store_true")
    args = parser.parse_args()
    if not args.fill_sheet and not args.coverage and not args.archive:
        parser.error("Choose --fill-sheet, --coverage, or --archive.")
    jobs, queries, labels = load()
    if args.fill_sheet:
        fill_sheet(jobs, queries)
    if args.coverage:
        coverage(jobs, queries, labels)
    if args.archive:
        archive_supplemental(jobs, queries, labels)


if __name__ == "__main__":
    main()
