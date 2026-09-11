"""Preview, insert, or verify 120 clearly labelled demo opportunities.

Dependencies: PyMySQL and bcrypt. Credentials come from environment variables
or the Git-ignored application-secrets.properties file, never from this script.
Default execution only builds a local preview. Use --apply for database writes.
"""

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import html
import json
import os
from pathlib import Path
import re
import secrets
import ssl
import sys
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "target" / "python-tools"))
BATCH = "HDJ-DEMO-20260911"
PUBLISHER_EMAIL = "demo-publisher@hotdevjobs.invalid"

# Salary bands are illustrative sample data, not verified compensation claims.
COUNTRIES = {
    "IN": {
        "name": "India", "currency": "INR", "scale": " LPA",
        "bands": [(3, 5), (6, 10), (12, 20), (22, 32), (32, 45)],
        "cities": [("Bengaluru", "Karnataka"), ("Hyderabad", "Telangana"),
                   ("Pune", "Maharashtra"), ("Mumbai", "Maharashtra"),
                   ("Chennai", "Tamil Nadu"), ("Gurugram", "Haryana"),
                   ("Noida", "Uttar Pradesh"), ("Ahmedabad", "Gujarat"),
                   ("Kolkata", "West Bengal"), ("Kochi", "Kerala")],
        "companies": ["Asterbridge Technologies", "Cedarbyte Works", "Monsoon Orbit",
                      "Juniper Grove Digital", "Copperleaf Analytics"],
    },
    "UK": {
        "name": "United Kingdom", "currency": "GBP", "scale": " / year",
        "bands": [(24000, 30000), (32000, 44000), (48000, 65000), (68000, 85000), (90000, 115000)],
        "cities": [("London", "England"), ("Manchester", "England"),
                   ("Bristol", "England"), ("Leeds", "England"),
                   ("Birmingham", "England"), ("Cambridge", "England"),
                   ("Edinburgh", "Scotland"), ("Glasgow", "Scotland"),
                   ("Cardiff", "Wales"), ("Belfast", "Northern Ireland")],
        "companies": ["Thistlepath Digital", "Bramble Quay Labs", "Alderwick Systems",
                      "Lantern Vale Studio", "Rowancrest Partners"],
    },
    "US": {
        "name": "United States", "currency": "USD", "scale": " / year",
        "bands": [(42000, 55000), (60000, 85000), (95000, 130000), (140000, 175000), (180000, 225000)],
        "cities": [("Austin", "Texas"), ("Seattle", "Washington"),
                   ("New York", "New York"), ("Boston", "Massachusetts"),
                   ("Denver", "Colorado"), ("Atlanta", "Georgia"),
                   ("Chicago", "Illinois"), ("San Diego", "California"),
                   ("Raleigh", "North Carolina"), ("Portland", "Oregon")],
        "companies": ["Canyon Finch Labs", "Blue Mesa Works", "Sequoia Lantern",
                      "Harbor Kite Systems", "Prairie Ember Digital"],
    },
    "CA": {
        "name": "Canada", "currency": "CAD", "scale": " / year",
        "bands": [(40000, 52000), (58000, 78000), (85000, 115000), (120000, 150000), (155000, 185000)],
        "cities": [("Toronto", "Ontario"), ("Vancouver", "British Columbia"),
                   ("Montreal", "Quebec"), ("Ottawa", "Ontario"),
                   ("Calgary", "Alberta"), ("Edmonton", "Alberta"),
                   ("Waterloo", "Ontario"), ("Halifax", "Nova Scotia"),
                   ("Winnipeg", "Manitoba"), ("Victoria", "British Columbia")],
        "companies": ["Maple Cairn Technologies", "Birchlight Studio", "Aurora Pebble Labs",
                      "Spruce Harbour Works", "Snowcap Orchard Systems"],
    },
}

# title, experience, tools/skills, two substantial responsibilities, salary band
ROLES = [
    ("Graduate Software Engineer", "0-1 years", "Java, object-oriented programming, Git, SQL",
     "Implement small backend features for an internal operations platform, translating acceptance criteria into readable code and demonstrating your changes during team reviews.",
     "Write unit tests, reproduce reported defects, and work through code-review feedback with a mentor while learning how services are released and monitored.", 0),
    ("Frontend Developer - React", "2-4 years", "React, TypeScript, HTML, CSS, accessibility",
     "Build responsive account and reporting screens from design specifications, paying attention to keyboard navigation, clear loading states, and consistent behaviour on smaller screens.",
     "Integrate REST endpoints, manage client-side state, and add component tests that cover validation errors, empty results, and the key actions users complete every day.", 2),
    ("Senior Java Backend Engineer", "5-8 years", "Java 17, Spring Boot, PostgreSQL, REST APIs, JUnit",
     "Own backend services for a multi-tenant workflow product, designing clear API contracts and handling authorisation, validation, transactions, and predictable error responses.",
     "Investigate slow queries and production incidents, improve service observability, and guide design reviews so teammates understand the operational trade-offs behind each change.", 3),
    ("Python Backend Developer", "2-4 years", "Python, FastAPI, PostgreSQL, pytest, Docker",
     "Develop Python APIs for a document-processing application, including queued tasks, structured validation, and endpoints that expose the progress of long-running work.",
     "Add regression tests and database migrations, troubleshoot failed processing runs, and document interfaces so frontend and integration teams can work independently.", 2),
    ("Full Stack Engineer", "3-5 years", "TypeScript, Node.js, React, SQL, integration testing",
     "Deliver customer-facing workflow features across the browser and server, breaking larger requirements into small releases with clear acceptance criteria and measurable outcomes.",
     "Improve form validation, API performance, and audit trails, and maintain integration tests around account access, data updates, and other important user journeys.", 2),
    ("Android Developer", "2-4 years", "Kotlin, Jetpack Compose, Android SDK, REST APIs",
     "Build mobile screens for a field-service application, including offline-friendly forms, synchronisation status, and accessible navigation for users working away from a desk.",
     "Diagnose crashes and battery usage, test across supported devices, and coordinate app releases with backend changes while keeping upgrade behaviour predictable.", 2),
    ("iOS Engineer", "3-5 years", "Swift, SwiftUI, XCTest, concurrency, networking",
     "Create and maintain native iOS workflows for scheduling and task management, with careful handling of network interruptions, permissions, and application state transitions.",
     "Review performance traces, expand automated UI coverage, and work with designers to deliver consistent interactions that follow the application's accessibility requirements.", 2),
    ("QA Automation Engineer", "2-4 years", "Playwright, Selenium, API testing, SQL, CI pipelines",
     "Create maintainable automated checks for critical web and API journeys, selecting assertions that catch meaningful regressions without depending on fragile implementation details.",
     "Triage test failures, document reproducible defects, and collaborate with developers to improve test data, release confidence, and the reliability of continuous integration.", 1),
    ("DevOps Engineer", "3-5 years", "Linux, Docker, Kubernetes, Terraform, CI/CD",
     "Maintain deployment pipelines and cloud infrastructure for business applications, defining repeatable environments and safe release procedures that engineering teams can follow.",
     "Improve alerts, secrets handling, and recovery documentation, and investigate deployment failures together with service owners before proposing changes to infrastructure defaults.", 2),
    ("Senior Site Reliability Engineer", "5-8 years", "Kubernetes, Prometheus, Grafana, Linux, incident response",
     "Define service-level indicators for key workflows, improve operational dashboards, and work with product engineers to reduce the sources of recurring reliability incidents.",
     "Lead incident reviews and recovery exercises, assess capacity needs, and document practical runbooks that allow the wider team to respond consistently under pressure.", 3),
    ("Cloud Solutions Architect", "8-12 years", "AWS, Azure, networking, security architecture, cost modelling",
     "Design cloud deployment approaches for application teams, mapping functional needs to networking, identity, storage, resilience, and cost considerations before implementation begins.",
     "Review architecture proposals with engineering and security stakeholders, document migration milestones, and help teams evaluate alternatives using small technical proofs of concept.", 4),
    ("Junior Data Analyst", "1-2 years", "SQL, Excel, Power BI, data cleaning, basic statistics",
     "Prepare recurring operational reports by combining source extracts, checking data quality, and documenting the definitions behind the measures used by business stakeholders.",
     "Explore changes in conversion and service metrics, create clear visual summaries, and communicate limitations so the team can distinguish useful signals from incomplete data.", 1),
    ("Data Engineer", "3-5 years", "Python, SQL, Airflow, dbt, data modelling",
     "Build data pipelines that move operational records into an analytics warehouse, with checks for freshness, completeness, duplicates, and unexpected changes in source schemas.",
     "Develop reusable transformations, improve slow workloads, and document lineage and ownership so analysts can understand which datasets are suitable for their reporting needs.", 2),
    ("Machine Learning Engineer", "3-5 years", "Python, PyTorch, scikit-learn, model evaluation, MLOps",
     "Develop and evaluate models for document categorisation and search relevance, using reproducible datasets and evaluation criteria agreed with product and data stakeholders.",
     "Package models for reliable inference, track quality and latency after release, and document failure cases and limitations before recommending wider use of a model.", 3),
    ("Analytics Engineer", "2-4 years", "SQL, dbt, Snowflake, Git, dimensional modelling",
     "Turn raw business data into documented analytics models, defining consistent measures for revenue, product usage, and operational performance across reporting teams.",
     "Add data tests and review transformation changes, resolve discrepancies with source owners, and help dashboard authors select the correct grain and joins for analysis.", 2),
    ("Cybersecurity Analyst", "2-4 years", "SIEM, log analysis, incident triage, networking, vulnerability management",
     "Review security alerts and investigate suspicious activity using endpoint, identity, and network evidence, keeping a clear record of findings and escalation decisions.",
     "Support vulnerability remediation, refine detection rules, and prepare actionable guidance for system owners while handling investigation data through approved internal channels.", 2),
    ("Product Designer", "3-5 years", "Figma, interaction design, prototyping, usability testing, design systems",
     "Design end-to-end workflows for a business productivity application, exploring alternatives through prototypes and testing how clearly users understand the proposed interactions.",
     "Maintain reusable interface patterns, prepare detailed engineering handoffs, and assess released screens for accessibility, consistency, and alignment with the original user problem.", 2),
    ("UX Researcher", "2-4 years", "user interviews, usability studies, research planning, synthesis",
     "Plan and run interviews and usability sessions to understand how customers organise daily work, with research questions connected to upcoming product decisions.",
     "Organise findings into clear themes, distinguish observations from assumptions, and share practical recommendations with designers and engineers without exposing participant information.", 2),
    ("Product Manager", "4-6 years", "discovery, prioritisation, product analytics, roadmaps, stakeholder communication",
     "Own the problem definition and delivery priorities for a product area, combining user feedback, support evidence, and usage data into a focused roadmap.",
     "Write outcome-oriented requirements, agree release success measures, and coordinate design and engineering discussions so decisions remain traceable as priorities evolve.", 3),
    ("Business Analyst", "2-4 years", "requirements analysis, process mapping, SQL, user stories, UAT",
     "Map current business processes and clarify requirements for workflow improvements, identifying dependencies, exceptions, and data needs through structured stakeholder discussions.",
     "Prepare user stories and acceptance criteria, support user-acceptance testing, and maintain decision records so delivery teams understand both the change and its purpose.", 1),
    ("Technical Support Engineer", "1-3 years", "Linux, SQL, API troubleshooting, ticket management, technical writing",
     "Investigate customer-reported product issues by reproducing behaviour, checking relevant logs, and gathering the information engineering needs to identify the underlying cause.",
     "Provide clear progress updates, maintain troubleshooting articles, and recognise recurring problems that could be resolved through product improvements or better onboarding guidance.", 1),
    ("Customer Success Manager", "3-5 years", "account planning, onboarding, CRM, product adoption, communication",
     "Guide business customers through onboarding and adoption, building account plans around their intended outcomes and the workflows they need to establish successfully.",
     "Review usage and support patterns, coordinate follow-up actions with internal teams, and communicate renewal risks and customer feedback through a structured review process.", 2),
    ("Digital Marketing Specialist", "2-4 years", "SEO, paid search, campaign analytics, GA4, copywriting",
     "Plan and deliver digital campaigns for a software product, matching audience needs to useful landing-page content and clearly defined campaign success measures.",
     "Analyse channel performance, run small creative and conversion experiments, and report results with transparent assumptions about attribution, sample size, and budget use.", 1),
    ("Content Strategist", "3-5 years", "editorial planning, technical content, SEO, CMS, content measurement",
     "Create an editorial plan that helps prospective customers understand the product, turning subject-matter interviews into accurate articles, guides, and customer-facing resources.",
     "Review existing content for clarity and usefulness, maintain publishing standards, and use engagement evidence to prioritise updates rather than producing volume without purpose.", 2),
    ("Financial Analyst", "2-4 years", "financial modelling, Excel, budgeting, variance analysis, reporting",
     "Prepare budget and forecasting models from approved financial inputs, checking assumptions and explaining the drivers behind differences between planned and actual performance.",
     "Maintain monthly reporting schedules, reconcile figures with source owners, and present concise analysis that helps managers understand spending patterns and operational trade-offs.", 2),
    ("Talent Acquisition Specialist", "2-4 years", "sourcing, ATS, structured interviews, recruitment reporting, scheduling",
     "Coordinate hiring for business and technical roles, translating agreed role requirements into clear sourcing plans and organised interview processes with consistent candidate communication.",
     "Maintain accurate recruitment records, report on process bottlenecks, and partner with hiring managers to improve interview feedback quality and the overall candidate experience.", 1),
    ("Supply Chain Analyst", "2-4 years", "Excel, SQL, inventory planning, forecasting, process analysis",
     "Analyse inventory and fulfilment data to identify stock imbalances, late deliveries, and process delays, validating findings against the operational context supplied by teams.",
     "Maintain planning reports, evaluate the impact of proposed process changes, and coordinate with purchasing and logistics stakeholders to track agreed corrective actions.", 1),
    ("Implementation Consultant", "3-5 years", "requirements workshops, configuration, data migration, training, project delivery",
     "Translate customer workflows into application configuration, documenting the implementation scope and planning migrations with clear checks for data completeness and usability.",
     "Run configuration reviews and training sessions, coordinate issue resolution during rollout, and prepare a practical handover that allows customers to manage routine operations.", 2),
    ("Engineering Manager", "8-12 years", "team leadership, delivery planning, system design, mentoring, engineering operations",
     "Lead a product engineering team by clarifying delivery goals, removing cross-team blockers, and balancing feature development with reliability and maintainability work.",
     "Support engineers through regular feedback and development plans, improve planning and review practices, and keep stakeholders informed about progress, dependencies, and delivery risks.", 4),
    ("Software Engineering Intern", "0-1 years; students and recent graduates", "programming fundamentals, Git, JavaScript or Python, testing",
     "Contribute a scoped feature to an internal application with support from an assigned mentor, taking part in planning, implementation, testing, and a short demonstration.",
     "Investigate small defects, improve developer documentation, and ask questions during code review while building familiarity with collaborative engineering practices and release workflows.", 0),
]


def generate_jobs():
    jobs = []
    for country_index, (code, country) in enumerate(COUNTRIES.items()):
        for index, (title, experience, skills, task1, task2, band) in enumerate(ROLES):
            city, state = country["cities"][(index + country_index * 3) % 10]
            company = country["companies"][index % 5] + " (Demo)"
            remote = ["Office-Only", "Partial-Remote", "Remote-Only"][(index + country_index) % 3]
            if index == 29:
                employment = "Internship"
            elif index in (17, 23):
                employment = "Freelance"
            elif index in (11, 20):
                employment = "Part-time"
            else:
                employment = "Full-time"
            low, high = country["bands"][band]
            salary = f"{country['currency']} {low:,}-{high:,}{country['scale']}"
            if employment == "Part-time":
                salary += " (full-time equivalent; pro rata)"
            elif employment == "Freelance":
                salary = {"IN": "INR 1,000-2,000 / hour", "UK": "GBP 250-400 / day",
                          "US": "USD 45-75 / hour", "CA": "CAD 45-70 / hour"}[code]
            arrangement = {
                "Office-Only": f"The sample role is based on site in {city}, with day-to-day collaboration taking place in the local office",
                "Partial-Remote": f"The sample role follows a hybrid arrangement in {city}, with two or three planned office days and the remaining work completed remotely",
                "Remote-Only": f"The sample role is remote within {country['name']}, with agreed overlap during the local team's working hours and documented asynchronous handovers",
            }[remote]
            marker = f"{BATCH}:{code}-{index + 1:02}"
            bullets = [task1, task2,
                       f"Bring {experience} of relevant experience, or equivalent practical work that demonstrates the skills needed for this role. Be prepared to discuss your contribution, the decisions you made, and what you learned from a project or assignment.",
                       f"Use {skills} in the day-to-day work. Explain your approach clearly, check the accuracy of your output, and maintain documentation that helps another team member understand and continue the work when needed.",
                       "Work closely with the relevant product, operations, or business stakeholders to agree priorities and acceptance criteria. Share progress in regular team reviews, surface dependencies early, and use feedback to improve the quality of the final deliverable.",
                       f"{arrangement}. The example compensation is {salary}; it is illustrative demo data. A portfolio, project example, or relevant work summary can help demonstrate your suitability for the scope described above."]
            introduction = (f"This sample {title} opportunity at {company} illustrates a role within a growing business-services team in {city}, {country['name']}. "
                            "The work combines ownership of practical deliverables with collaboration, clear documentation, and regular feedback from the people who use the results.")
            description = ("<p><strong>Demo listing: fictional company and vacancy, created to demonstrate the portal. No real employer is recruiting through this post.</strong></p>"
                           f"<p>{html.escape(introduction)}</p><h3>Responsibilities and requirements</h3><ul>"
                           + "".join(f"<li>{html.escape(bullet)}</li>" for bullet in bullets)
                           + f"</ul><!-- {marker} -->")
            jobs.append(dict(marker=marker, title=title, company=company, city=city, state=state,
                             country=country["name"], employment=employment, remote=remote,
                             salary=salary, experience=experience, skills=skills, description=description))
    assert len(jobs) == 120 and len({j["marker"] for j in jobs}) == 120
    assert all(j["description"].count("<li>") == 6 and len(j["description"]) < 10000 for j in jobs)
    return jobs


def connect():
    import pymysql
    local = ROOT / "application-secrets.properties"
    settings = {}
    if local.exists():
        settings = dict(line.split("=", 1) for line in local.read_text(encoding="utf-8-sig").splitlines()
                        if "=" in line and not line.lstrip().startswith("#"))
    settings.update({k: os.environ[k] for k in ("DB_URL", "DB_USERNAME", "DB_PASSWORD") if k in os.environ})
    url = urlparse(settings["DB_URL"].removeprefix("jdbc:"))
    if url.scheme != "mysql" or not url.hostname or not url.path.strip("/"):
        raise ValueError("Expected a MySQL URL with a database name")
    return pymysql.connect(host=url.hostname, port=url.port or 3306, user=settings["DB_USERNAME"],
                           password=settings["DB_PASSWORD"], database=url.path.lstrip("/"),
                           ssl=ssl.create_default_context(), charset="utf8mb4", autocommit=False,
                           connect_timeout=15, read_timeout=40, write_timeout=40)


def fingerprint(rows):
    return hashlib.sha256(json.dumps(rows, default=str, sort_keys=True).encode()).hexdigest()


def inspect_batch(cursor):
    cursor.execute("SELECT j.job_post_id,j.job_title,j.description_of_job,l.country,j.job_company_id,j.job_location_id,j.posted_by_id "
                   "FROM job_post_activity j JOIN job_location l ON l.id=j.job_location_id "
                   "WHERE j.description_of_job LIKE %s ORDER BY j.job_post_id", (f"%<!-- {BATCH}:%",))
    return cursor.fetchall()


def validate_batch(rows, jobs):
    expected = {j["marker"]: j for j in jobs}
    found = {}
    for row in rows:
        markers = re.findall(r"<!-- (" + re.escape(BATCH) + r":[A-Z]{2}-\d{2}) -->", row[2])
        if len(markers) != 1 or markers[0] in found or markers[0] not in expected:
            raise RuntimeError("Duplicate or unexpected seed marker; no further writes performed")
        marker = markers[0]
        if row[2] != expected[marker]["description"] or row[1] != expected[marker]["title"]:
            raise RuntimeError("An existing seed listing differs from the manifest; refusing to overwrite it")
        found[marker] = row[0]
    return found


def apply_jobs(jobs):
    import bcrypt
    conn = connect()
    try:
        with conn.cursor() as cursor:
            cursor.execute("SELECT * FROM job_post_activity ORDER BY job_post_id")
            before = cursor.fetchall()
            before_ids = [row[0] for row in before]
            # Read the actual PK position instead of relying on schema column order.
            columns = [col[0] for col in cursor.description]
            before_ids = [row[columns.index("job_post_id")] for row in before]
            cursor.execute("SELECT user_id FROM users WHERE email=%s FOR UPDATE", (PUBLISHER_EMAIL,))
            publisher = cursor.fetchone()
            now = datetime.now(timezone.utc).replace(tzinfo=None)
            if publisher:
                publisher_id = publisher[0]
            else:
                cursor.execute("SELECT user_type_id FROM users_type WHERE user_type_name='Recruiter'")
                role = cursor.fetchone()
                if not role:
                    raise RuntimeError("Recruiter role is missing")
                password_hash = bcrypt.hashpw(secrets.token_bytes(32), bcrypt.gensalt()).decode()
                cursor.execute("INSERT INTO users (email,password,is_active,registration_date,user_type_id,created_at,updated_at) "
                               "VALUES (%s,%s,0,%s,%s,%s,%s)", (PUBLISHER_EMAIL, password_hash, now, role[0], now, now))
                publisher_id = cursor.lastrowid
                cursor.execute("INSERT INTO recruiter_profile (user_account_id,first_name,last_name,company,designation,company_description,created_at,updated_at) "
                               "VALUES (%s,%s,%s,%s,%s,%s,%s,%s)",
                               (publisher_id, "Demo", "Publisher", "HotDevJobs Demo Collection", "Sample data publisher",
                                "Fictional opportunities for demonstrating the job portal. This account does not represent an employer.", now, now))
            existing = validate_batch(inspect_batch(cursor), jobs)
            missing = [j for j in jobs if j["marker"] not in existing]
            company_ids, location_ids = {}, {}
            for job in missing:
                if job["company"] not in company_ids:
                    cursor.execute("INSERT INTO job_company (name,logo,created_at,updated_at) VALUES (%s,'',%s,%s)", (job["company"], now, now))
                    company_ids[job["company"]] = cursor.lastrowid
                location = (job["city"], job["state"], job["country"])
                if location not in location_ids:
                    cursor.execute("INSERT INTO job_location (city,state,country,created_at,updated_at) VALUES (%s,%s,%s,%s,%s)", (*location, now, now))
                    location_ids[location] = cursor.lastrowid
            values = [(j["description"], j["title"], j["employment"], now, j["remote"], j["salary"],
                       company_ids[j["company"]], location_ids[(j["city"], j["state"], j["country"])], publisher_id, now, now) for j in missing]
            if values:
                cursor.executemany("INSERT INTO job_post_activity (description_of_job,job_title,job_type,posted_date,remote,salary,job_company_id,job_location_id,posted_by_id,created_at,updated_at) "
                                   "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", values)
            rows = inspect_batch(cursor)
            found = validate_batch(rows, jobs)
            if len(found) != 120:
                raise RuntimeError("Post-insert validation did not find all 120 jobs")
            if before_ids:
                cursor.execute("SELECT * FROM job_post_activity WHERE job_post_id IN (" + ",".join(["%s"] * len(before_ids)) + ") ORDER BY job_post_id", before_ids)
                if fingerprint(cursor.fetchall()) != fingerprint(before):
                    raise RuntimeError("Pre-existing jobs changed during the batch; rolling back")
            cursor.execute("SELECT COUNT(*) FROM job_post_activity")
            total = cursor.fetchone()[0]
            receipt = {"batch": BATCH, "inserted": len(missing), "already_present": len(existing),
                       "total_jobs": total, "countries": dict(Counter(r[3] for r in rows)),
                       "publisher_id": publisher_id, "job_ids": sorted(found.values()),
                       "six_bullets_verified": all(r[2].count("<li>") == 6 for r in rows),
                       "existing_jobs_unchanged": True, "committed_at_utc": now.isoformat()}
            conn.commit()
        return receipt
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true")
    mode.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    jobs = generate_jobs()
    output = ROOT / "target" / "seed-demo-jobs"
    output.mkdir(parents=True, exist_ok=True)
    if args.apply:
        receipt = apply_jobs(jobs)
        (output / "receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
        print(json.dumps(receipt, indent=2))
    elif args.verify:
        conn = connect()
        try:
            with conn.cursor() as cursor:
                rows = inspect_batch(cursor)
                found = validate_batch(rows, jobs)
                if len(found) != 120:
                    raise RuntimeError("Expected 120 persisted demo jobs")
                cursor.execute("SELECT COUNT(*) FROM job_post_activity")
                print(json.dumps({"verified_jobs": len(found), "total_jobs": cursor.fetchone()[0],
                                  "countries": dict(Counter(r[3] for r in rows)),
                                  "six_bullets_each": all(r[2].count("<li>") == 6 for r in rows)}))
        finally:
            conn.close()
    else:
        (output / "preview.json").write_text(json.dumps(jobs, indent=2), encoding="utf-8")
        print(json.dumps({"preview_jobs": len(jobs), "countries": dict(Counter(j["country"] for j in jobs)),
                          "description_lengths": [min(len(j["description"]) for j in jobs), max(len(j["description"]) for j in jobs)],
                          "bullets_each": 6, "database_writes": 0}))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Do not print exception payloads, which might contain connection details.
        print(f"Seed operation failed ({type(error).__name__}); credentials withheld.", file=sys.stderr)
        sys.exit(1)
