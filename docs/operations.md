# Deployment and operations

## Email verification

New accounts are created only after a six-digit email code is verified. Existing accounts remain usable. Codes expire after 10 minutes, are single-use, and are stored as BCrypt hashes alongside the hashed pending password. Pending registrations persist across restarts. Verification allows five incorrect attempts per hourly window; sending is limited to five codes per email per hour with at least 60 seconds between requests. Resending invalidates the old code without resetting failed attempts. Registration POSTs require CSRF tokens.

Before accepting signups:

1. Create a [Resend](https://resend.com) account and [verify a domain you own](https://resend.com/docs/dashboard/domains/introduction) by adding its DNS records. A Gmail address or the shared `onrender.com` domain cannot be your verified sender domain.
2. Create a sending API key. In Render → your service → Environment, add `RESEND_API_KEY`.
3. Add `EMAIL_FROM`, for example `HotDevJobs <verify@your-domain.com>`, using the verified domain. Keep `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `GEMINI_API_KEY`, and optional `GEMINI_MODEL`.
4. Save and redeploy. Register a test account, confirm the code arrives, verify it, and sign in. Automated tests mock email delivery and do not verify the live sender.

Delivery uses the [Resend HTTPS API](https://resend.com/docs/api-reference/emails/send-email), since [Render free services block SMTP ports](https://render.com/docs/free). No SMTP password is needed. Missing settings or delivery failure prevent new registration and show an error; existing users can still sign in. `/health` does not check email delivery. Check sending quotas in the provider dashboard.

## Search and recommendations

Public search and dashboard results are paginated in groups of 12. Experience filters match jobs whose stated requirement range overlaps the selected years; jobs without stated experience appear under “Not specified”. New job posts support nullable `min_experience_years` and `max_experience_years` columns, created through the existing Hibernate schema-update configuration. Existing posts use explicit year ranges in their descriptions until a recruiter supplies structured values.

Only GET requests for `/job-details-apply/{id}` are public. Applying, saving, editing and accessing profiles remain authenticated; applicant lists are rendered only for the posting recruiter.

The September 13 expansion added 350 sample postings (200 India; 50 each UK, US and Canada) and cleaned the repeated demo wording from the original 120. Six description bullets and a short sample notice remain. The catalogue now contains 478 jobs, including the eight original non-seeded postings.

`GET /api/search/jobs?q=...` suggests titles, keywords and companies from posted jobs. `GET /api/search/locations?q=...` combines posted cities/states/countries with [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api), based on GeoNames data. The demo's non-commercial geocoding endpoint needs no key; commercial use requires the appropriate provider plan. Results are bounded and cached; manual search works during provider failures.

The dashboard renders 12 jobs per page and preserves filters, sort and applied/saved views while paging. Ranking runs locally using target role, headline and skills. Preferred city and workplace are secondary signals for relevant jobs; no profile data goes to an AI service. If nothing matches, posting date determines order. Explicit Newest and Title sorting remain available. The current implementation ranks the matching collection in memory before slicing the page; a larger catalogue should move ranking and paging into database queries.

## Optional AI assistant

The dashboard now includes two AI workflows:

- Job seekers: enter a target role and skills to generate interview questions and a practice plan.
- Recruiters: enter a role and requirements to generate a job-description draft, then review and copy it into the existing job-posting form.

Create a key in [Google AI Studio](https://aistudio.google.com/apikey). Google's [pricing page](https://ai.google.dev/gemini-api/docs/pricing) lists a free tier for Gemini 3.5 Flash-Lite; quotas and model availability vary by project and may change. Check your project's active limits in AI Studio. Free usage is limited, not unlimited. A project with billing enabled can incur charges.

Set the following in your IDE's run configuration, Windows environment variables, or Render's secret environment variables, then restart the application:

```text
GEMINI_API_KEY=your-key
GEMINI_MODEL=gemini-3.5-flash-lite
```

`GEMINI_MODEL` is optional and defaults to the model above. Never put the key in HTML, JavaScript, Git, or screenshots. See Google's [key setup guide](https://ai.google.dev/gemini-api/docs/api-key).

The browser calls the authenticated Spring endpoint `POST /api/ai/assist`; the backend calls Gemini's [generateContent API](https://ai.google.dev/api/generate-content). The server selects the task from the signed-in user's role. Requests require explicit consent, contain at most 3,000 characters, and are limited to one request per 30 seconds per session. Connection/read timeouts and output limits keep requests bounded. The UI handles missing configuration, quota errors and failed requests, and renders responses as plain text.

Only text deliberately entered into the assistant is sent. Profiles, resumes, applications and database records are not automatically shared. Google's pricing table states that free-tier content may be used to improve its products: use non-sensitive sample role descriptions when experimenting. AI output is a draft for human review, and is never automatically published or used to rank applicants.

Without a key, all other portal features work and the assistant explains that it has not been configured. Live provider calls require your own key; automated tests mock the provider. Before a larger public rollout, add account-level quotas backed by a shared store (the current session limit resets on a new session), and choose a provider/data policy suitable for your users.

Potential follow-up AI features: a user-approved resume feedback tool, cover-letter drafting from verified experience, and skill-gap explanations against a selected role. These are not implemented yet.

## Verification

Run `mvn test`. Dashboard tests cover role-specific rendering, recruiter search ownership, applied/saved views, sorting, empty states, and AI authorization/validation/throttling. Provider tests cover response parsing and safe error handling without making external AI calls.

To export rendered dashboard fixtures for visual inspection, run `mvn test -Dtest=DashboardTests -Ddashboard.preview=true`. HTML files are written under the ignored `target/dashboard-preview` directory using mock data only.

## Render health check and UptimeRobot

`GET /health` returns HTTP 200 and `{"status":"UP"}` without authentication, database queries, or AI requests. It also supports HEAD and disables caching. This is a process liveness check, not a database readiness check. The Render blueprint uses `/health`. For a manually managed service, set **Settings → Health Checks → Health Check Path** to `/health` after deployment.

1. Deploy these changes. Open `https://job-portal-0lgp.onrender.com/health` and confirm the JSON above. The first request can still have a cold start.
2. Sign in to [UptimeRobot](https://uptimerobot.com/) and select **Add New Monitor**.
3. Choose **HTTP(s) / Website** and name it **HotDevJobs health**.
4. Enter `https://job-portal-0lgp.onrender.com/health` (replace the hostname if yours differs).
5. Set the interval to **5 minutes**, use GET if offered, and select your alert contact. No authentication, API key or custom headers are needed.
6. Create the monitor and confirm it becomes **Up**.

See the official [UptimeRobot setup guide](https://help.uptimerobot.com/en/articles/11358364-how-to-create-your-first-monitor-on-uptimerobot-quick-setup-guide). The monitor must be created in your account; this code does not create it or deploy itself.

[Render's free-service documentation](https://render.com/docs/free) specifies 15 minutes without inbound traffic before sleep and 750 free instance hours per workspace each month. Five-minute external checks should prevent ordinary inactivity sleep while they reach the service, but cannot guarantee uptime during restarts, outages or quota exhaustion. One service running continuously uses up to 744 hours in a 31-day month; other free services share the same allowance. Render's internal health checks do not replace the external monitor for this purpose.

## Application workflow and resume comparison

- `/applications` gives candidates their own applications, 12 per page, with per-application timelines.
- `/recruiter/candidates` queries only the signed-in recruiter's applications, 24 per page, filtered in the database by job, status, name/skill and experience. Columns group the current page by status.
- `/applications/{id}` is visible only to the applicant and posting recruiter. Only the posting recruiter can change status or private notes. Returning to an earlier status is allowed to correct a decision; history is retained. A revision check under a database row lock prevents stale submissions overwriting changes.
- Existing application rows with a null status display as Applied. Hibernate schema update adds nullable status, recruiter notes and workflow revision fields, plus the application status event table. Initial timeline entries use the existing application date.
- Resume/profile access is limited to the candidate and recruiters whose jobs they applied to, including direct candidate-file URLs.
- `/resume-comparison/{jobId}` is candidate-only. Text PDFs up to 3 MB and 10 pages can be extracted from the profile resume. Scanned, encrypted, missing or unsupported files fall back to manual text entry. Candidates must review and consent before text is sent to Gemini; extracted and generated text is not persisted by this feature.
- Comparison shares the 30-second AI cooldown and existing `GEMINI_API_KEY`/`GEMINI_MODEL` configuration. No additional environment variables are required. Provider calls are mocked in automated tests.

Email delivery remains unchanged. `spring-boot-starter-mail` provides an SMTP client, not an email delivery service. Render free web services block outbound ports 25, 465 and 587, so Gmail SMTP is unavailable there. The existing HTTPS mail integration avoids those ports. Sending specifically as a Gmail account through HTTPS requires a separate Gmail API OAuth setup.
