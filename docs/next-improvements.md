# Improvements for a recruiter reviewing this project

The strongest next step is a complete hiring workflow that demonstrates permissions, state changes and useful feedback. More sample listings help exercise search and pagination, but the following features show deeper engineering work.

| Priority | Implementation | What a reviewer can try | Important validation |
| --- | --- | --- | --- |
| 1 | Application pipeline: Applied → Reviewing → Interview → Offered / Rejected, with a history of changes | A recruiter moves a candidate; the candidate sees the updated status | Only the posting recruiter can change status; reject invalid transitions; retain timestamps |
| 2 | Explain job recommendations with “target role matched”, “skills matched”, and preferred-location signals | Change profile skills and see the ordering and explanations change | Deterministic results, newest-job fallback, no invented qualifications |
| 3 | Draft, published and closed job states with an expiry date | Save an unfinished draft, publish it, then close applications | Closed jobs reject new applications on the server; editing another employer’s post is forbidden |
| 4 | Recruiter hiring dashboard: applications by status and time to first response | Filter by the recruiter’s own role and date range | Counts come from real application records; another recruiter’s data stays private |
| 5 | CI checks and deployment smoke tests | Inspect a passing build and repeat the documented checks | Tests use an isolated database; credentials never enter logs or artifacts |

Before a wider public launch, move uploads to private object storage, use signed download links, apply consistent CSRF protection to authenticated writes, and move catalogue ranking/pagination into database queries as volume grows. Keep sample vacancies clearly distinguished from active employer postings.

Already implemented: separate recruiter/job-seeker dashboards, profile validation, job posting/editing, saved/applied views, profile-based ordering, 12-job pagination, AI writing assistance, search suggestions, location menus, and the public health endpoint. Email-provider changes are deferred at the owner's request.
