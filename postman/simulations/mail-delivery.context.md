# Mail delivery simulation

Created 2026-10-25. The local handler models only the endpoints called by `VerificationMailService`, using [Google OAuth](https://developers.google.com/identity/protocols/oauth2), [Gmail send](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/send), and [Resend errors](https://resend.com/docs/api-reference/errors). No owner-published mock was available in this session; Orbit discovery was unreachable. Responses are synthetic examples, not captured production traffic.

## Purpose

Exercise the real Java HTTP client and fallback on Gmail token rejection, missing access tokens, Gmail send failures, non-JSON errors and timeouts. The handler's `x-mail-case` header selects test failures. Unknown endpoints return 501. All credentials and recipients are fake; no real email is sent.

## Execution

Start from the repository root in one terminal:

```powershell
postman simulation run postman/simulations/mail-delivery.sim.yaml --port 4900 --no-history --output ndjson
```

Wait for `listening`, then use Java 17 in another terminal:

```powershell
mvn "-Dtest=VerificationMailSimulationTests" "-Dmail.simulation=true" test
```

Tests rewrite outbound URLs to `http://127.0.0.1:4900/mail` using a test-only interceptor, retaining the real request factory and timeouts. Override with `-Dmail.simulation.url=...` if needed. Stop the simulation with Ctrl+C. Keep logs under ignored `target/`.

## Assumptions

- 2026-10-25: This verifies HTTP error handling, not real credentials, sender permissions, delivery to an inbox or Render connectivity. The handler does not validate every provider request field; existing `VerificationMailServiceTests` check request construction.
- 2026-10-25: A timeout may occur after Gmail accepts the email. Fallback sends the same code, so duplicate messages remain possible.

## Observations

<!-- begin:observations -->
- 2026-10-25: All seven simulation tests passed. The mail member served OAuth, Gmail and Resend traffic; none of those endpoint groups stayed silent. Confirmed 400 token errors, 503/HTML 502 send failures, a ten-second client read timeout, successful fallback, failure with no fallback, and failure of both providers. Checked that logs omit private provider descriptions and credentials.
<!-- end:observations -->

## What didn't work

- 2026-10-25: CLI 1.74.0 rejected a directory-valued member path; using `config.yaml` resolved it. The sandbox denied access to Postman's local dataset runtime; the run succeeded with that access permitted.
