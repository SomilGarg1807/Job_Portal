#!/usr/bin/env python3
"""Generate reproducible, entirely synthetic jobs and draft relevance labels.

This script does not connect to TiDB or Gemini. Existing labels are never
overwritten unless --refresh-draft-labels is explicitly supplied.
"""

from __future__ import annotations

import argparse
import json
import random
from collections import Counter
from datetime import date, timedelta
from pathlib import Path


ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"
SEED = 20261004
JOBS_PER_FAMILY = 24

# Fictional companies are explicitly marked so none can be mistaken for a real employer.
COMPANIES = (
    "Synthetic Alder Systems", "Synthetic Beacon Works", "Synthetic Cedar Labs",
    "Synthetic Delta Software", "Synthetic Ember Digital", "Synthetic Fieldstone Tech",
    "Synthetic Grove Products", "Synthetic Harbor Platform", "Synthetic Juniper Cloud",
    "Synthetic Meridian Apps", "Synthetic Northline Data", "Synthetic Orchard Studio",
)
LOCATIONS = (
    ("Pune", "Maharashtra", "India"),
    ("Bengaluru", "Karnataka", "India"),
    ("Hyderabad", "Telangana", "India"),
    ("Delhi", "Delhi", "India"),
    ("Chennai", "Tamil Nadu", "India"),
    ("London", "England", "United Kingdom"),
    ("Toronto", "Ontario", "Canada"),
    ("Austin", "Texas", "United States"),
)
EXPERIENCE = ((0, 1), (1, 3), (3, 5), (5, 8), (8, 12), (12, 20))
JOB_TYPES = ("Full-Time", "Full-Time", "Full-Time", "Part-Time", "Freelance")
WORK_MODES = ("Remote-Only", "Office-Only", "Partial-Remote")

# Each family has anchors that appear in every posting, with rotating specialist
# skills and responsibilities to make individual postings meaningfully different.
FAMILIES = {
    "backend_java": {
        "titles": ("Java Backend Developer", "Spring Boot Engineer", "API Platform Engineer",
                   "Software Development Engineer (Java)", "Junior Java Developer", "Senior Backend Engineer (Java)"),
        "anchor": ("Java", "Spring Boot"),
        "specialist": ("SQL", "REST APIs", "Kafka", "PostgreSQL", "OAuth", "Microservices", "Docker", "JUnit"),
        "tasks": ("build resilient payment APIs", "improve service reliability", "design event-driven services",
                  "maintain account management APIs", "reduce latency in backend systems", "integrate partner services"),
    },
    "frontend_react": {
        "titles": ("React Frontend Developer", "UI Engineer (React)", "JavaScript Web Developer",
                   "Frontend Software Engineer", "React UI Developer", "Web Application Engineer"),
        "anchor": ("React", "JavaScript"),
        "specialist": ("TypeScript", "Accessibility", "CSS", "Playwright", "GraphQL", "Design Systems", "Vite", "REST APIs"),
        "tasks": ("build accessible customer dashboards", "improve web performance", "deliver responsive account flows",
                  "maintain reusable UI components", "create data-heavy web screens", "test browser interactions"),
    },
    "data_engineering": {
        "titles": ("Data Engineer", "ETL Developer", "Data Platform Engineer",
                   "Analytics Engineer", "Pipeline Engineer", "Senior Data Engineer"),
        "anchor": ("SQL", "Python"),
        "specialist": ("Kafka", "Airflow", "Snowflake", "Spark", "dbt", "BigQuery", "Data Modeling", "AWS"),
        "tasks": ("build dependable ingestion pipelines", "model warehouse datasets", "orchestrate daily data loads",
                  "improve streaming data quality", "support analytics-ready tables", "monitor pipeline failures"),
    },
    "data_analytics": {
        "titles": ("Data Analyst", "BI Analyst", "Product Analyst",
                   "Reporting Analyst", "Analytics Specialist", "SQL Analyst"),
        "anchor": ("SQL", "Dashboards"),
        "specialist": ("Power BI", "Tableau", "Python", "A/B Testing", "Snowflake", "Statistics", "Excel", "dbt"),
        "tasks": ("explain customer behavior", "measure product experiments", "create operational dashboards",
                  "define trustworthy metrics", "investigate conversion changes", "prepare stakeholder reports"),
    },
    "devops_sre": {
        "titles": ("DevOps Engineer", "Site Reliability Engineer", "Platform Reliability Engineer",
                   "Cloud Operations Engineer", "SRE", "Infrastructure Automation Engineer"),
        "anchor": ("Kubernetes", "Terraform"),
        "specialist": ("AWS", "Prometheus", "Incident Response", "CI/CD", "Linux", "Observability", "Docker", "Python"),
        "tasks": ("reduce incident recovery time", "automate cloud deployments", "improve service availability",
                  "build deployment pipelines", "monitor production systems", "manage infrastructure as code"),
    },
    "qa_automation": {
        "titles": ("QA Automation Engineer", "Software Test Engineer", "SDET",
                   "Quality Engineer", "Test Automation Developer", "Manual QA Analyst"),
        "anchor": ("Test Automation", "API Testing"),
        "specialist": ("Playwright", "Selenium", "Java", "Cypress", "JUnit", "Performance Testing", "Postman", "CI/CD"),
        "tasks": ("build stable regression suites", "test critical checkout flows", "find API contract defects",
                  "improve release confidence", "design browser test coverage", "triage intermittent test failures"),
    },
    "mobile_android": {
        "titles": ("Android Developer", "Kotlin Mobile Engineer", "Mobile Application Developer",
                   "Android Software Engineer", "Mobile UI Engineer", "Senior Android Engineer"),
        "anchor": ("Kotlin", "Android"),
        "specialist": ("Jetpack Compose", "Coroutines", "REST APIs", "Room", "Firebase", "Accessibility", "Gradle", "Testing"),
        "tasks": ("build offline-friendly Android features", "improve mobile startup time", "maintain account screens",
                  "integrate mobile APIs", "refine app accessibility", "ship reliable app updates"),
    },
    "mobile_ios": {
        "titles": ("iOS Developer", "Swift Mobile Engineer", "iOS Software Engineer",
                   "Mobile App Engineer (iOS)", "iOS UI Engineer", "Senior iOS Engineer"),
        "anchor": ("Swift", "iOS"),
        "specialist": ("SwiftUI", "UIKit", "REST APIs", "Core Data", "XCTest", "Accessibility", "Combine", "App Store"),
        "tasks": ("build polished iPhone features", "improve mobile startup time", "maintain subscription screens",
                  "integrate mobile APIs", "refine app accessibility", "ship reliable app updates"),
    },
    "security": {
        "titles": ("Security Engineer", "Application Security Engineer", "Cloud Security Engineer",
                   "SOC Analyst", "Security Operations Engineer", "Product Security Engineer"),
        "anchor": ("Threat Modeling", "Security Monitoring"),
        "specialist": ("OAuth", "IAM", "AWS", "Incident Response", "Web Security", "Python", "SIEM", "Vulnerability Assessment"),
        "tasks": ("protect account sign-in", "review application threats", "improve cloud access controls",
                  "investigate security alerts", "secure public APIs", "coordinate incident response"),
    },
    "product_management": {
        "titles": ("Product Manager", "Technical Product Manager", "Product Owner",
                   "Associate Product Manager", "Growth Product Manager", "Platform Product Manager"),
        "anchor": ("Roadmapping", "User Research"),
        "specialist": ("A/B Testing", "Analytics", "Backlog Management", "APIs", "Stakeholder Management", "SQL", "Experimentation", "Discovery"),
        "tasks": ("prioritize product opportunities", "define a platform roadmap", "learn from user interviews",
                  "measure feature adoption", "coordinate cross-functional delivery", "improve onboarding"),
    },
    "ux_design": {
        "titles": ("UX Designer", "Product Designer", "UI/UX Designer",
                   "Interaction Designer", "UX Researcher", "Design Systems Designer"),
        "anchor": ("Figma", "User Research"),
        "specialist": ("Accessibility", "Prototyping", "Usability Testing", "Design Systems", "Interaction Design", "Visual Design", "Wireframing", "Information Architecture"),
        "tasks": ("design accessible workflows", "test prototypes with users", "improve complex forms",
                  "maintain reusable design patterns", "simplify onboarding", "research customer needs"),
    },
    "cloud_architecture": {
        "titles": ("Cloud Architect", "AWS Solutions Architect", "Azure Cloud Engineer",
                   "Cloud Infrastructure Engineer", "Kubernetes Platform Engineer", "Infrastructure Architect"),
        "anchor": ("Cloud Architecture", "Infrastructure Design"),
        "specialist": ("AWS", "Azure", "Kubernetes", "Terraform", "Networking", "IAM", "Cost Optimization", "Observability"),
        "tasks": ("design resilient cloud systems", "plan secure migration paths", "improve infrastructure costs",
                  "standardize platform architecture", "review cloud network designs", "guide service deployments"),
    },
}

# Draft intent is kept out of queries.json so a later runner cannot accidentally
# rank with it. It only creates reviewable labels, never trusted ground truth.
DRAFT_INTENT = {
    "q01": {"primary": ("backend_java",)}, "q02": {"primary": ("frontend_react",)},
    "q03": {"primary": ("data_engineering",)}, "q04": {"primary": ("data_analytics",)},
    "q05": {"primary": ("qa_automation",)}, "q06": {"primary": ("mobile_android",)},
    "q07": {"primary": ("security",)}, "q08": {"primary": ("ux_design",)},
    "q09": {"primary": ("backend_java",)},
    "q10": {"primary": ("frontend_react",)},
    "q11": {"primary": ("data_engineering",), "secondary": ("data_analytics",), "secondarySkills": ("Snowflake", "dbt")},
    "q12": {"primary": ("devops_sre",), "secondary": ("cloud_architecture",), "secondarySkills": ("Kubernetes", "Terraform", "Observability")},
    "q13": {"primary": ("qa_automation",)},
    "q14": {"primary": ("mobile_ios",)},
    "q15": {"primary": ("data_analytics",), "secondary": ("data_engineering",), "secondarySkills": ("Snowflake", "dbt")},
    "q16": {"primary": ("data_engineering", "backend_java"), "skill": "Kafka"},
    "q17": {"primary": ("devops_sre", "cloud_architecture"), "skill": "Terraform"},
    "q18": {"primary": ("qa_automation", "frontend_react"), "skill": "Playwright"},
    "q19": {"primary": ("ux_design",), "skill": "Figma"},
    "q20": {"primary": ("mobile_android",), "skill": "Coroutines"},
    "q21": {"primary": ("data_engineering", "data_analytics"), "skill": "Snowflake"},
    "q22": {"primary": ("security", "backend_java"), "skill": "OAuth"},
    "q23": {"primary": ("backend_java",)}, "q24": {"primary": ("frontend_react",)},
    "q25": {"primary": ("data_engineering",)}, "q26": {"primary": ("devops_sre",)},
    "q27": {"primary": ("mobile_ios",)}, "q28": {"primary": ("cloud_architecture",)},
    "q29": {"primary": ("backend_java",)}, "q30": {"primary": ("frontend_react",)},
    "q31": {"primary": ("qa_automation",)}, "q32": {"primary": ("data_analytics",)},
    "q33": {"primary": ("mobile_android", "mobile_ios")},
    "q34": {"primary": ("devops_sre",), "secondary": ("cloud_architecture", "security"), "secondarySkills": ("Observability", "Kubernetes", "Incident Response")},
    "q35": {"primary": ("data_analytics",), "secondary": ("product_management",), "secondarySkills": ("A/B Testing", "Analytics", "Experimentation")},
    "q36": {"primary": ("security",), "secondary": ("backend_java",), "secondarySkills": ("OAuth",)},
    "q37": {"primary": ("ux_design",), "secondary": ("frontend_react",), "secondarySkills": ("Accessibility", "Design Systems")},
    "q38": {"primary": ()}, "q39": {"primary": ()}, "q40": {"primary": ()},
}


def generate_jobs() -> dict:
    rng = random.Random(SEED)
    jobs = []
    for family_index, (family, spec) in enumerate(FAMILIES.items()):
        for index in range(JOBS_PER_FAMILY):
            title = spec["titles"][index % len(spec["titles"])]
            city, state, country = LOCATIONS[(index * 5 + family_index * 3) % len(LOCATIONS)]
            if title.startswith(("Junior ", "Associate ")):
                experience_options = EXPERIENCE[:2]
            elif "Senior " in title or "Architect" in title:
                experience_options = EXPERIENCE[3:]
            elif "Product Manager" in title or title == "Product Owner":
                experience_options = EXPERIENCE[2:5]
            else:
                experience_options = EXPERIENCE
            minimum, maximum = experience_options[(index + family_index) % len(experience_options)]
            extras = spec["specialist"]
            skills = list(dict.fromkeys((*spec["anchor"], extras[index % len(extras)],
                                         extras[(index + 3) % len(extras)], extras[(index + 5) % len(extras)])))
            task = spec["tasks"][index % len(spec["tasks"])]
            second_task = spec["tasks"][(index + 2) % len(spec["tasks"])]
            description = (
                f"<p>Our synthetic team is hiring a {title} to {task}. The role works with "
                f"engineering and product partners to deliver reliable software.</p>"
                f"<h3>Responsibilities</h3><ul><li>{task.capitalize()}.</li>"
                f"<li>{second_task.capitalize()} and document tradeoffs.</li></ul>"
                f"<h3>Requirements</h3><ul><li>{minimum} to {maximum} years of relevant experience.</li>"
                f"<li>Hands-on work with {', '.join(skills[:3])}.</li>"
                f"<li>Comfort with {', '.join(skills[3:])} and collaborative delivery.</li></ul>"
            )
            jobs.append({
                "key": f"{family}-{index + 1:03d}",
                "family": family,
                "title": title,
                "company": COMPANIES[(index + family_index * 2) % len(COMPANIES)],
                "location": {"city": city, "state": state, "country": country},
                "minExperienceYears": minimum,
                "maxExperienceYears": maximum,
                "jobType": JOB_TYPES[(index + family_index) % len(JOB_TYPES)],
                "remote": WORK_MODES[(index + family_index * 2) % len(WORK_MODES)],
                "postedDate": (date(2026, 9, 1) + timedelta(days=rng.randrange(30))).isoformat(),
                "skills": skills,
                "descriptionOfJob": description,
            })
    return {"schemaVersion": 1, "synthetic": True, "seed": SEED, "jobs": jobs}


def eligible(job: dict, filters: dict) -> bool:
    location = filters.get("location", "").casefold()
    if location and not any(location in value.casefold() for value in job["location"].values()):
        return False
    if "jobType" in filters and job["jobType"] != filters["jobType"]:
        return False
    if "remote" in filters and job["remote"] != filters["remote"]:
        return False
    if "postedAfter" in filters and job["postedDate"] < filters["postedAfter"]:
        return False
    if "experience" in filters:
        selected = filters["experience"]
        lower, upper = (12, 50) if selected == "12+" else map(int, selected.split("-"))
        if job["minExperienceYears"] > upper or job["maxExperienceYears"] < lower:
            return False
    return True


def draft_grade(job: dict, query: dict, intent: dict) -> int | None:
    if not eligible(job, query["filters"]):
        return None
    primary = intent["primary"]
    secondary = intent.get("secondary", ())
    skill = intent.get("skill")
    if query["category"] == "skill_only":
        if skill not in job["skills"]:
            return None
        return 2 if job["family"] in primary else 1
    if job["family"] in primary:
        if query["category"] == "exact_title":
            return 2 if job["title"].casefold() == query["text"].casefold() else 1
        return 2
    if job["family"] in secondary and any(skill in job["skills"] for skill in intent["secondarySkills"]):
        return 1
    return None


def spaced_sample(items: list[dict], limit: int) -> list[dict]:
    """Spread a small review pool across job keys rather than taking the first rows."""
    if len(items) <= limit:
        return items
    if limit == 1:
        return [items[len(items) // 2]]
    return [items[i * (len(items) - 1) // (limit - 1)] for i in range(limit)]


def generate_draft_labels(jobs: list[dict], queries: list[dict]) -> dict:
    result = []
    for query in queries:
        intent = DRAFT_INTENT[query["id"]]
        full_draft = {}
        for job in jobs:
            grade = draft_grade(job, query, intent)
            if grade is not None:
                full_draft[job["key"]] = grade

        # This is an initial review pool, not an exhaustive relevance set. Stage 3
        # must add any unjudged retrieved jobs to a second review queue before scoring.
        strong = spaced_sample([job for job in jobs if full_draft.get(job["key"]) == 2], 4)
        partial = spaced_sample([job for job in jobs if full_draft.get(job["key"]) == 1], 2)
        judgments = {job["key"]: full_draft[job["key"]] for job in strong + partial}

        # Include filter failures and jobs from other families with overlapping
        # technology, then a few diverse distractors. These zeroes need review too.
        negatives = [job for job in jobs if job["key"] not in full_draft]
        wrong_filter = [job for job in negatives if job["family"] in intent["primary"]]
        wrong_family = [job for job in negatives if job["family"] not in intent["primary"]]
        anchors = {skill for family in intent["primary"] for skill in FAMILIES[family]["anchor"]}
        overlapping = [job for job in wrong_family if anchors.intersection(job["skills"])]
        diverse, used_families = [], set()
        for job in wrong_family:
            if job["family"] not in used_families:
                diverse.append(job)
                used_families.add(job["family"])
        if query["category"] == "no_answer":
            selected = diverse[:8]
        else:
            selected = spaced_sample(wrong_filter, 2) + spaced_sample(overlapping, 2)
            selected.extend(job for job in diverse if job not in selected)
            selected = selected[:4]
        for job in selected:
            judgments[job["key"]] = 0
        result.append({
            "queryId": query["id"],
            "reviewStatus": "DRAFT - NOT GROUND TRUTH",
            "intentNote": (
                "No suitable job is intended in this synthetic catalogue. Review any returned job carefully."
                if query["category"] == "no_answer" else
                "Provisional family/skill judgment; verify title, description and every structured filter."
            ),
            "judgments": dict(sorted(judgments.items())),
        })
    return {
        "schemaVersion": 1,
        "reviewStatus": "DRAFT - NOT GROUND TRUTH",
        "notice": "Agent-drafted initial review pool. A human must review and correct every explicit judgment. Unlisted jobs are UNJUDGED, not irrelevant; later retrieved jobs need supplemental review before metrics.",
        "grades": {"0": "irrelevant or filter-ineligible", "1": "relevant", "2": "highly relevant"},
        "queries": result,
    }


def validate(jobs_doc: dict, queries_doc: dict, labels_doc: dict) -> None:
    jobs, queries = jobs_doc["jobs"], queries_doc["queries"]
    assert 250 <= len(jobs) <= 300
    assert 10 <= len({job["family"] for job in jobs}) <= 15
    assert len(queries) == 40
    assert len({job["key"] for job in jobs}) == len(jobs)
    assert all(job["maxExperienceYears"] <= 3 for job in jobs
               if job["title"].startswith(("Junior ", "Associate ")))
    assert all(job["minExperienceYears"] >= 5 for job in jobs
               if "Senior " in job["title"] or "Architect" in job["title"])
    assert len({query["id"] for query in queries}) == len(queries)
    assert set(DRAFT_INTENT) == {query["id"] for query in queries}
    assert len(labels_doc["queries"]) == len(queries)
    keys = {job["key"] for job in jobs}
    by_id = {item["queryId"]: item for item in labels_doc["queries"]}
    assert set(by_id) == {query["id"] for query in queries}
    for query in queries:
        judgments = by_id[query["id"]]["judgments"]
        assert judgments and set(judgments).issubset(keys)
        assert set(judgments.values()).issubset({0, 1, 2})
        if not labels_doc.get("explicitReviewCompleted"):
            if query["category"] == "no_answer":
                assert not any(grade > 0 for grade in judgments.values())
            else:
                assert any(grade == 2 for grade in judgments.values()), query["id"]
        assert any(grade == 0 for grade in judgments.values())
        for job in jobs:
            if judgments.get(job["key"], 0) > 0:
                assert eligible(job, query["filters"]), (query["id"], job["key"])
        if query["category"] == "skill_only":
            assert all(query["text"].casefold() not in job["title"].casefold() for job in jobs)
    assert Counter(query["category"] for query in queries) == {
        "exact_title": 8, "synonym": 7, "skill_only": 7, "location_role": 6,
        "filtered": 5, "natural_language": 4, "no_answer": 3,
    }


def serialize(document: dict) -> str:
    return json.dumps(document, ensure_ascii=False, indent=2) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate existing files without changing them")
    parser.add_argument("--refresh-draft-labels", action="store_true", help="overwrite draft labels; discards human edits")
    args = parser.parse_args()
    queries = json.loads((DATA / "queries.json").read_text(encoding="utf-8"))
    jobs = generate_jobs()
    jobs_path = DATA / "jobs.json"
    labels_path = DATA / "labels-draft.json"
    if args.check:
        assert jobs_path.read_text(encoding="utf-8") == serialize(jobs), "jobs.json is not the deterministic generated output"
        labels = json.loads(labels_path.read_text(encoding="utf-8"))
    else:
        jobs_path.write_text(serialize(jobs), encoding="utf-8")
        if args.refresh_draft_labels or not labels_path.exists():
            labels_path.write_text(serialize(generate_draft_labels(jobs["jobs"], queries["queries"])), encoding="utf-8")
        labels = json.loads(labels_path.read_text(encoding="utf-8"))
    validate(jobs, queries, labels)
    counts = Counter(query["category"] for query in queries["queries"])
    judgment_count = sum(len(item["judgments"]) for item in labels["queries"])
    print(f"Validated {len(jobs['jobs'])} synthetic jobs in {len(FAMILIES)} families, "
          f"{len(queries['queries'])} queries, {judgment_count} draft judgments.")
    print("Query categories:", ", ".join(f"{name}={count}" for name, count in sorted(counts.items())))
    print("No database or API calls were made.")


if __name__ == "__main__":
    main()
