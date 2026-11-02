# CLAUDE.md: rules for every session in this repo

You are building a take-home assignment for Isocode Labs: **Brief 4, a gated township resident app** (Pune, 2,400 flats, 28 towers). The owner of this repo is Maniram (GitHub: ManiraM-1). He is primarily a Java/Spring Boot developer and is new to frontend.

## Start of every session (do this first, every time)
1. Read `docs/PROGRESS.md` to see what is done and what step is next.
2. Read `docs/PLAN.md` for that step's exact scope and acceptance checks.
3. Re-read `docs/BRIEF.md` whenever a requirement is unclear. It is the only source of truth.
4. Tell Maniram in two lines what step you are on and what you will build, then build it.

Never skip ahead of `docs/PLAN.md`. Never build an optional feature before every core step is ticked.

## Source of truth and assumptions
- Use only the information in `docs/BRIEF.md`. Do not invent requirements.
- Anything the brief does not state (a due date, a late-fee rate, a CSV format, an SLA) is an assumption. Log it in `docs/ASSUMPTIONS.md` with the next ID (A1, A2, ...), the decision, and the reason, in the same commit that introduces it. Make such values configurable, not hardcoded in logic.
- If a decision would change what the client sees or how money moves, and the plan does not already decide it, stop and ask Maniram.

## Non-negotiable product rules (from the brief; each must have a test)
1. **Unclear payments are never posted to a flat automatically.** They go to the accounts queue. A human confirms.
2. **Safety words always escalate to the duty manager by rule, whatever the AI says.** The rule runs independently of the AI and cannot be overridden by it.
3. **Duplicate complaints are linked, never closed.** Each resident keeps their own complaint and sees it is linked.
4. **The help desk can change a complaint's category in one tap.** The correction is recorded.
5. **A resident can only see and act on their own flat's data.** Every flat-scoped endpoint checks membership server-side.
6. **Money is exact.** Use `BigDecimal` (scale 2, `RoundingMode.HALF_UP`) in Java and `NUMERIC(12,2)` in PostgreSQL. Never `double` or `float` for money.
7. **The ledger is append-only.** Corrections are new reversing entries, never edits or deletes.
8. **Re-importing the same bank statement never double-posts.**
9. **No dead buttons.** If a feature is not built, its button does not exist.

## Stack (do not change without asking)
- Backend: Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Data JPA, Spring Security, Bean Validation, Flyway, PostgreSQL 16.
- Auth: phone number + OTP (mocked SMS), then a JWT in an **httpOnly, Secure, SameSite=Lax cookie**. Not localStorage.
- AI: Google Gemini via REST (`GEMINI_API_KEY`, `GEMINI_MODEL` from env). Always with a timeout and a deterministic fallback.
- Frontend: React 18 + Vite + TypeScript + Tailwind CSS + React Router + TanStack Query. Mobile-first.
- Tests: JUnit 5 + AssertJ + Mockito for logic; Spring Boot tests with Testcontainers PostgreSQL for integration; Vitest for the few frontend logic tests.
- Local DB: `docker-compose.yml` with PostgreSQL 16.
- Deployment: one Docker image. The built frontend is served by Spring Boot as static files (same origin, so cookies just work). Hosted on Render; database on Neon.

## Repository layout
```
/backend            Spring Boot app (package com.isocode.township)
/frontend           React app
/docs               BRIEF, PLAN, PROGRESS, ASSUMPTIONS, TDD source
/samples            sample bank statement CSVs used for demo and tests
docker-compose.yml
Dockerfile
README.md
deployment.md
.env.example
```
Backend packages by feature, not by layer: `auth`, `flat`, `billing`, `payment`, `matching`, `complaint`, `ai`, `committee`, `common`. Inside each: entity, repository, service, controller, dto.

## Code style
- **No explanatory comments in code.** Make names self-explanatory instead. Explain the code to Maniram in chat, not in the file.
- Small, single-purpose classes. Services hold logic; controllers only map HTTP to services.
- Pure logic (bill calculation, late fee, matching decisions, safety rule, duplicate scoring) lives in plain classes with no Spring or DB dependency, so it can be unit-tested directly.
- Validate every request body with Bean Validation. Return errors as RFC 7807 problem details from one `@RestControllerAdvice`.
- Never log OTPs, JWTs, API keys or full phone numbers.

## Secrets
- Secrets live only in `.env` (git-ignored). Commit `.env.example` with names and dummy values.
- Never hardcode a key, password or secret, even in tests. Tests use generated values.
- Before every commit, check that the diff contains no secret.

## Commits (the reviewers read the history)
- One logical step per commit, as listed in `docs/PLAN.md`. Small and in order.
- Commit as soon as a meaningful, working unit is done, not only at the end of a step. A large step may have 2–4 commits (for example: migration and entities, then pure logic with its tests, then the service and API with integration tests). Every commit must build and pass all tests. Push after every commit. Never batch several steps into one commit.
- Before committing, all of these must pass: `cd backend && ./mvnw -q verify`, and once the frontend exists, `cd frontend && npm run build && npm test -- --run`.
- Message format (Conventional Commits), English, imperative:
  ```
  feat(matching): match bank rows by flat reference and UPI ID

  <one to three lines on what changed and why>
  ```
  Commits contain no AI attribution trailer.
- Never force-push, squash, amend a pushed commit, or rewrite history.
- After each commit, push to `origin main`.

## After every step
1. Tick the step in `docs/PLAN.md`.
2. Append to `docs/PROGRESS.md`: date, step number, what was built, test count, any new assumption IDs, anything left open.
3. Commit the doc update with the step (same commit) or immediately after.
4. Tell Maniram, in plain language, what you built and which files matter most, so he can explain it in the interview. He prefers that every term is explained, with the actual code shown.

## Design rules (design counts as much as features)
- Minimal and calm: lots of white space, one accent colour, clear type, nothing on screen without a purpose.
- Resident home shows dues, open complaints (and today's visitors only once visitors are built). Every resident action is within two taps of home.
- Every screen has loading, empty and error states. Every form shows inline validation.
- Works at 360px width first, then scales up. Touch targets at least 44px.
- Numbers in money and tables use tabular figures and are right-aligned. Rupees formatted with Indian grouping (₹1,23,456.00).
