# Deployment and operations

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
