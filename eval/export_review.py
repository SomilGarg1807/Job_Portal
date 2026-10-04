#!/usr/bin/env python3
"""Export draft judgments with job context for human review; never score or seed."""

from __future__ import annotations

import argparse
import csv
import json
import re
from html import unescape
from pathlib import Path


DATA = Path(__file__).resolve().parent / "data"


def read(name: str) -> dict:
    return json.loads((DATA / name).read_text(encoding="utf-8"))


def plain_html(value: str) -> str:
    return " ".join(unescape(re.sub(r"<[^>]+>", " ", value)).split())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--force", action="store_true", help="replace an existing CSV; discards review entries")
    args = parser.parse_args()
    jobs = {job["key"]: job for job in read("jobs.json")["jobs"]}
    queries = {query["id"]: query for query in read("queries.json")["queries"]}
    labels = read("labels-draft.json")
    if not labels["reviewStatus"].startswith("DRAFT"):
        raise SystemExit("The review export only accepts DRAFT labels.")
    target = DATA / "labels-review-341-DRAFT.csv"
    if target.exists() and not args.force:
        raise SystemExit(f"{target.name} already exists; use --force only if it has no human review entries.")
    columns = (
        "reviewStatus", "queryId", "category", "queryText", "filters", "intentNote",
        "jobKey", "draftGrade", "reviewedGrade", "reviewNote", "jobTitle", "family", "city", "country",
        "experienceYears", "jobType", "remote", "skills", "description",
    )
    with target.open("w", encoding="utf-8-sig", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=columns)
        writer.writeheader()
        for item in labels["queries"]:
            query = queries[item["queryId"]]
            for key, grade in item["judgments"].items():
                job = jobs[key]
                writer.writerow({
                    "reviewStatus": item["reviewStatus"],
                    "queryId": query["id"],
                    "category": query["category"],
                    "queryText": query["text"],
                    "filters": json.dumps(query["filters"], sort_keys=True),
                    "intentNote": item["intentNote"],
                    "jobKey": key,
                    "draftGrade": grade,
                    "reviewedGrade": "",
                    "reviewNote": "",
                    "jobTitle": job["title"],
                    "family": job["family"],
                    "city": job["location"]["city"],
                    "country": job["location"]["country"],
                    "experienceYears": f"{job['minExperienceYears']}-{job['maxExperienceYears']}",
                    "jobType": job["jobType"],
                    "remote": job["remote"],
                    "skills": "; ".join(job["skills"]),
                    "description": plain_html(job["descriptionOfJob"]),
                })
    print(f"Exported {sum(len(item['judgments']) for item in labels['queries'])} DRAFT judgments to {target.name}.")


if __name__ == "__main__":
    main()
