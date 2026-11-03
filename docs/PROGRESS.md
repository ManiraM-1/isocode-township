# Progress log

Newest entry at the bottom. Claude Code appends one entry after every step. If a session drops, the next session reads this file first and continues from "Next step".

**Next step:** Step 4

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

## 2026-11-03: Step 2, Data model and seed data
- Built: Flyway `V1__core_schema.sql` with tower, flat, app_user, flat_member, society_settings (single row, defaults 6.50/sqft, due day 10, 21% p.a., A14 SLA minutes, AI threshold 0.60), audit_log, outbox_message. Database checks: positive area, flat code format `T00-0000`, E.164 phone, duty manager and skills only for staff, one membership per user per flat, one settings row. JPA entities and repositories in `flat`, `auth`, `common`; `ComplaintCategory` enum (A13) for staff skills. `DemoDataSeeder` (Random(42), timestamps from the injected Clock) seeds 6 towers, the 3 brief flats exactly, 45 more flats (650–1,800 sq ft, about 20% tenanted, about 25% with a family member) and the 6 help-desk staff; `DemoDataSeedRunner` runs it at startup only when `APP_DEMO_MODE=true` and the flat table is empty.
- Tests: 24 passing (8 schema, 7 seeder incl. exact brief flats and determinism, 9 earlier). Integration tests now share one context and one Postgres container via `@IntegrationTest`.
- New assumptions: none. Modelling note: the T12-0408 owner (Vivek Bhosale, fictional) and owners of other tenanted flats have no phone on file, so they cannot sign in. Non-demo seed residents use phones `+9191000xxxxx`.
- Open items: audit_log and outbox_message entities are added in the steps that first write to them (Step 3 outbox, Step 5 audit).
- Next step: Step 3

## 2026-11-03: Step 3, Sign-in with OTP and roles
- Built: `V1_1__otp_challenge.sql` (named 1.1 so Step 4 keeps `V2__billing.sql`). `OtpService`: 6-digit code from `SecureRandom`, stored only as a BCrypt hash, 5-minute expiry, lock after 5 wrong attempts (row locked with `SELECT … FOR UPDATE` so parallel guesses cannot skip the count), max 3 requests per phone per 10 minutes → 429. Unknown or inactive phones get a real challenge and the identical response but no SMS, and a correct code for them is treated as wrong. Codes go to `outbox_message` (A20); `demoOtp` is returned only when `APP_DEMO_MODE=true`. `JwtService` (HS256, 12 h, sub = user id, role claim; secret must be ≥ 32 bytes or startup fails). Session in an httpOnly, Secure, SameSite=Lax `session` cookie. `GET /api/me`, `POST /api/auth/logout`. Spring Security: stateless, cookie-to-header CSRF (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header), 401/403 as problem details, `/api/desk/**` and `/api/committee/**` HELPDESK only, other `/api/**` signed in. `FlatAccessGuard.requireMember` (active member and active user) and `requireMemberOrStaff` (for read endpoints the plan opens to HELPDESK).
- Tests: 63 passing (11 OTP unit, 5 JWT unit, 16 HTTP sign-in flow, 6 flat guard, 25 earlier). All plan cases covered. "Resident 403 on another flat" is tested on the guard now; Step 4 adds the HTTP-level check once the first flat endpoint exists. Tests use a real double-submit CSRF helper (`SameOriginCsrf`) instead of Spring Security Test's `csrf()`, which swaps the CSRF repository in the shared context. Manual smoke test on real Tomcat: full curl sign-in, cookies carry SameSite=Lax, no OTP or JWT in the log.
- New assumptions: none.
- Open items: logout clears the cookie but a copied JWT stays valid until it expires (stateless; acceptable for 12 h, a deny-list is possible later). Old `otp_challenge` rows are not purged yet. Demo `.env.example` JWT secret is a placeholder; the deployed app must set its own (Step 18).
- Next step: Step 4
