# HotDevJobs

**A full-stack recruitment portal with personalized job discovery, recruiter workflows, and AI-assisted career preparation.**

[Explore the application](https://job-portal-0lgp.onrender.com/)

HotDevJobs brings job seekers and recruiters into one application. Candidates can discover relevant roles, build a professional profile, save opportunities, and apply. Recruiters can publish openings, review applicants, and manage their company profile.

## Core workflows

| Job seekers | Recruiters |
| --- | --- |
| Discover jobs matched to a target role and skills | Publish and edit detailed job postings |
| Search by keyword, company, location, and work arrangement | Review candidates who applied to their openings |
| Save opportunities and track submitted applications | View posting and application counts |
| Maintain skills, experience, preferences, and a resume | Maintain a recruiter and company profile |
| Prepare for interviews with AI-generated practice plans | Draft job descriptions with AI assistance |

## Features

- **Application tracking:** Applied, Shortlisted, Interview, Offered and Rejected statuses, with dated candidate timelines.
- **Recruiter candidate board:** filter applicants by job, status, name, skill and experience; review profiles and resumes, and keep private notes.
- **Safe workflow updates:** recruiter ownership checks, CSRF protection, retained history and stale-update detection.
- **Personalized discovery:** profile-based ordering prioritizes relevant roles and uses posting date when no useful match exists.
- **Focused browsing:** 12 jobs per page in public search and the dashboard, with sorting and filters for experience, employment type, workplace, and posting date.
- **Public job details:** visitors can read descriptions and requirements before signing in; applications, saved jobs, and recruiter tools require authentication.
- **Search suggestions:** title and company suggestions come from posted jobs; location suggestions cover cities, states, and countries.
- **Consistent controls:** keyboard-accessible location menus, responsive filters, clear selection states, and country/state/city inputs across profiles and job posting.
- **Complete profiles:** skills, experience, employment preferences, resumes, and recruiter company information.
- **Job management:** rich-text descriptions, ownership checks for editing, candidate review, and saved/applied views.
- **Account verification:** email-code verification with expiry, resend limits, and single-use codes.
- **Responsive design:** dedicated recruiter and job-seeker dashboards with profile completion, activity counts, and clear empty states.

The catalogue includes a varied sample collection across India, the UK, the US, and Canada, allowing the search and application workflows to be explored with realistic roles, skills, and experience levels.

## AI assistance

Google Gemini supports **job-description drafting**, **interview preparation**, and **resume-to-job comparison**. Candidates can review text extracted from their uploaded PDF, or paste relevant resume text, before comparing it with a selected job. The comparison highlights relevant evidence, requirements not evidenced, resume improvements and preparation topics.

The backend selects the task from the signed-in user's role. Requests require consent, enforce input limits, and apply a cooldown. Generated content remains a draft for the user to review. Credentials stay on the server, and profiles and resumes are not automatically sent to the AI provider.

Job recommendations use local profile matching rather than AI-generated suitability scores.

## Architecture

```mermaid
flowchart LR
    UI["Thymeleaf · CSS · JavaScript"] --> Security["Spring Security"]
    Security --> Controllers["Spring MVC controllers"]
    Controllers --> Services["Application services"]
    Services --> Persistence["Spring Data JPA · Hibernate"]
    Persistence --> Database[("MySQL / TiDB")]
    Services --> AI["Google Gemini"]
    Services --> Locations["Location API"]
```

Controllers handle requests, services coordinate application behavior, and repositories manage persistence. Entity relationships connect accounts, profiles, companies, locations, job posts, applications, and saved opportunities.

| Area | Technologies |
| --- | --- |
| Backend | Java 17, Spring Boot, Spring MVC |
| Security | Spring Security, BCrypt |
| Persistence | Spring Data JPA, Hibernate, MySQL-compatible database |
| Frontend | Thymeleaf, JavaScript, CSS, Bootstrap |
| AI integration | Google Gemini REST API |
| Tests | JUnit 5, Mockito, MockMvc, H2 |
| Build and delivery | Maven, Docker, Render |

## Quality and validation

Automated tests cover application startup, authentication redirects, recruiter ownership, dashboard rendering, profile validation, pagination, recommendation ordering, search-provider failures, AI request handling, and email verification.

Browser checks cover desktop and mobile layouts, keyboard navigation, location selection, filter controls, and the rich-text editor. The public `/health` endpoint supports service monitoring.

## Configuration

All credentials are supplied at runtime through environment variables. Nothing sensitive is committed to this repository.

**Required** — the application refuses to start without these, with a message naming what is missing:

| Variable | Purpose |
| --- | --- |
| `DB_URL` | JDBC URL of the MySQL-compatible database (TiDB Cloud in production) |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |

**Required in production:**

| Variable | Purpose |
| --- | --- |
| `REMEMBER_ME_KEY` | Signs the 7-day "keep me signed in" cookie. Must stay stable across deploys; generate with `openssl rand -hex 32`. |

**Optional** — each feature disables itself cleanly when its variables are absent:

| Variable | Purpose |
| --- | --- |
| `GEMINI_API_KEY` | Enables the AI assistant and resume comparison |
| `GEMINI_MODEL` | Overrides the default Gemini model |
| `RESEND_API_KEY`, `EMAIL_FROM` | Sends signup verification codes through Resend |
| `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GMAIL_REDIRECT_URI`, `GMAIL_REFRESH_TOKEN`, `GMAIL_FROM` | Sends signup codes through the Gmail API (preferred when all are present) |
| `REDIS_URL`, `CACHE_TYPE` | Cache backend; set `CACHE_TYPE=none` to run without Redis |
| `PORT` | HTTP port, defaults to `8080` |

Signup requires one of the two email providers; without either, existing users can still sign in but no one can register.

**Local development.** Copy the template and fill in your own values. The copy is git-ignored and loaded automatically at startup:

```bash
cp application-local.properties.example application-local.properties
```

Environment variables and your IDE's run configuration both still work and take precedence.

**Render.** Set each variable under your service -> Environment. `render.yaml` already declares them with `sync: false`, so they are entered in the dashboard and never stored in the repository. `REMEMBER_ME_KEY` is generated once by the blueprint. See [docs/operations.md](docs/operations.md) for provider-by-provider setup.

## Getting started

Use Java 17 and a MySQL-compatible database. Follow the [configuration guide](docs/operations.md) for database and integration settings.

```bash
./mvnw spring-boot:run
```

Open `http://localhost:8080`. On Windows, use `mvnw.cmd`.

Run the automated tests:

```bash
./mvnw test
```
