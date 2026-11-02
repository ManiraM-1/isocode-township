# Progress log

Newest entry at the bottom. Claude Code appends one entry after every step. If a session drops, the next session reads this file first and continues from "Next step".

**Next step:** Step 2

---

<!-- Entry template
## YYYY-MM-DD: Step N, <title>
- Built: ...
- Tests: <count> passing
- New assumptions: A..
- Open items: ...
- Next step: Step N+1
-->

## 2026-11-02: Step 0, Repository rules and brief
- Built: git repository; `CLAUDE.md`, `docs/BRIEF.md`, `docs/PLAN.md`, `docs/PROGRESS.md`, `docs/ASSUMPTIONS.md` (A1–A21) committed; `.gitignore` (secrets, Java/Maven, Node/Vite, IDE, OS files); `.env.example` with variable names and dummy values; short `README.md`; `.gitattributes` (LF line endings so `mvnw` works in Linux containers); `.claude/settings.json` (no AI attribution trailer on commits or PRs).
- Tests: none yet (no code). `mvnw verify` and the frontend checks start in Step 1 and Step 13.
- New assumptions: none (A1–A21 were already written in the plan stage).
- Open items: `deployment.md` is linked from the README but is written in Step 18.
- Next step: Step 1

## 2026-11-02: Step 1, Backend skeleton
- Built: Spring Boot 3.5.16 / Java 21 Maven project in `backend/` with Maven wrapper (3.9.9); dependencies per plan (web, JPA, security, validation, Flyway + PostgreSQL module, actuator, commons-csv 1.14.1, jjwt 0.12.6, Testcontainers). `docker-compose.yml` runs PostgreSQL 16 with credentials from `.env`. `application.yml` reads every setting from env vars (also imports `.env` if present); DB password and `JWT_SECRET` have no default. `Clock` bean in `APP_TIMEZONE` (default Asia/Kolkata). `GlobalExceptionHandler` returns RFC 7807 problem details for validation (with per-field errors), not found, forbidden, conflict and unexpected errors (internal message hidden). `GET /api/health` → `{"status":"ok"}`, public; everything else needs sign-in (real auth comes in Step 3).
- Tests: 9 passing (context loads on Testcontainers PostgreSQL 16, clock zone, health public, other endpoints blocked; 5 problem-detail cases).
- New assumptions: none.
- Open items: unauthenticated requests currently get a plain 403 from Spring Security; Step 3 turns that into 401 problem details.
- Next step: Step 2
