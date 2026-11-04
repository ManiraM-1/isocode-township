# Build plan

Core first, then optional depth. Each step is one commit (sometimes two, noted). A step is done only when its **acceptance checks** pass and `./mvnw -q verify` (plus the frontend build and tests once they exist) is green. Tick the box when done.

Domain terms used below: a **flat code** looks like `T07-1203` (tower 07, flat 1203). **Outstanding** means unpaid bill principal plus posted late-fee charges, minus any advance. **Ledger** means the flat's append-only list of debits (bills, late fees) and credits (payments).

---

## Part 1: Core build (must work end to end)

### [x] Step 0: Repository rules and brief
Files: `CLAUDE.md`, `docs/BRIEF.md`, `docs/PLAN.md`, `docs/PROGRESS.md`, `docs/ASSUMPTIONS.md`, `.gitignore` (Java, Node, `.env`, IDE folders), `.env.example`, short `README.md` (one paragraph + "see deployment.md").
Commit: `docs: add client brief, build plan and working rules`

### [x] Step 1: Backend skeleton
- Spring Boot 3.x, Java 21, Maven wrapper, package `com.isocode.township`.
- Dependencies: web, data-jpa, security, validation, flyway (+ flyway-database-postgresql), postgresql, actuator, commons-csv, jjwt (or Spring's `NimbusJwtEncoder`), lombok not allowed (keep code explicit), test: spring-boot-starter-test, testcontainers postgresql, junit-jupiter.
- `docker-compose.yml` with PostgreSQL 16 (credentials read from `.env`).
- `application.yml` reads everything from env vars with safe local defaults for non-secrets only: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY`, `GEMINI_MODEL`, `APP_DEMO_MODE`, `APP_TIMEZONE` (default `Asia/Kolkata`).
- A `Clock` bean in `Asia/Kolkata`. All "today/now" logic uses this clock, never `LocalDate.now()` directly, so tests can fix time.
- Global `@RestControllerAdvice` returning RFC 7807 problem details for validation errors, not-found, forbidden, conflict.
- `GET /api/health` returns `{status:"ok"}`.
- Test: context loads against Testcontainers PostgreSQL.
Commit: `chore(backend): scaffold Spring Boot service with Postgres and Flyway`

### [x] Step 2: Data model and seed data
Flyway `V1__core_schema.sql` creates:
- `tower(id, code UNIQUE, name)`
- `flat(id, code UNIQUE, tower_id FK, area_sqft INT CHECK > 0)`
- `app_user(id, phone UNIQUE (E.164), full_name, role ('RESIDENT','HELPDESK'), is_duty_manager BOOL, skills TEXT[] (complaint categories, staff only), active BOOL, created_at)`
- `flat_member(id, flat_id FK, user_id FK, relation ('OWNER','TENANT','FAMILY'), active, UNIQUE(flat_id,user_id))`
- `society_settings` (single row): `rate_per_sqft NUMERIC(8,2)=6.50`, `due_day INT=10`, `late_interest_pct_pa NUMERIC(5,2)=21.00`, SLA minutes per urgency (A14), `ai_confidence_threshold NUMERIC(3,2)=0.60`.
- `audit_log(id, actor_user_id, action, entity_type, entity_id, details JSONB, created_at)`
- `outbox_message(id, to_user_id, channel ('SMS','WHATSAPP'), body, created_at)` (A20)

Seeder (`DemoDataSeeder`, runs only when `APP_DEMO_MODE=true` and the `flat` table is empty; deterministic `new Random(42)`; dates relative to the injected `Clock`):
- Towers T03, T07, T12, T15, T21, T26.
- The three brief flats **exactly**: T07-1203 Neeraj Kulkarni 1150 sq ft (owner); T12-0408 Swati Joshi (tenant) 980 sq ft, plus a fictional owner with no login; T21-1601 Aamir Shaikh 1640 sq ft (owner).
- About 45 more flats across the towers with fictional names, areas between 650 and 1,800 sq ft.
- Staff (all HELPDESK): Anjali Deshmukh (general desk, skills: all), Ravi Patil (Plumbing, Civil), Sunil More (Electrical), Prakash Jadhav (Lift), Meena Gaikwad (Housekeeping, Pest control), Kiran Shinde (duty manager, Security).
- Demo phones: residents `+919000000001` (Neeraj), `+919000000002` (Swati), `+919000000003` (Aamir); staff from `+919000000101` (Anjali) upward; Kiran `+919000000106`.
- Billing history and complaints are seeded in later steps (4, 5, 9), each in the step that introduces the tables.
Acceptance: a test runs the seeder on a fixed clock and asserts the three brief flats, their areas and member relations exactly.
Commit: `feat(core): add schema for towers, flats, users and settings with demo seed`

### [x] Step 3: Sign-in with OTP and roles
- `POST /api/auth/otp/request {phone}`: validates E.164 Indian mobile; creates `otp_challenge(id, phone, code_hash (BCrypt), expires_at = now+5 min, attempts, consumed_at)`; writes the code to `outbox_message`. Rate limit: max 3 requests per phone per 10 minutes → 429. Unknown phone returns the same 200 response as a known one (no account enumeration). In demo mode only, the response includes `demoOtp`.
- `POST /api/auth/otp/verify {phone, code}`: max 5 attempts per challenge, then locked; expired or consumed codes rejected. On success sets an httpOnly, Secure, SameSite=Lax cookie `session` holding a JWT (12 h, claims: user id, role) and returns `/me`.
- `GET /api/me` → `{id, name, role, isDutyManager, flats:[{id, code, relation}]}`. `POST /api/auth/logout` clears the cookie.
- Spring Security: stateless, JWT read from the cookie, CSRF on with `CookieCsrfTokenRepository` (frontend sends `X-XSRF-TOKEN`). `/api/auth/**` and `/api/health` public; `/api/desk/**` and `/api/committee/**` need HELPDESK; resident flat endpoints go through one `FlatAccessGuard.requireMember(user, flatId)`.
- Never log the OTP or JWT.
Tests: correct OTP logs in; wrong OTP increments attempts; sixth attempt rejected; expired rejected; reused rejected; rate limit 429; unknown phone same response; resident gets 403 on another flat; resident gets 403 on `/api/desk/**`.
Commit: `feat(auth): add phone OTP sign-in with JWT cookie and role-based access`

### [x] Step 4: Maintenance billing and late fees
Flyway `V2__billing.sql`:
- `bill(id, flat_id, period DATE (1st of month), area_sqft, rate, amount NUMERIC(12,2), due_date, amount_paid NUMERIC(12,2) DEFAULT 0, created_at, UNIQUE(flat_id, period))`
- `late_fee_charge(id, flat_id, bill_id, from_date, to_date, amount, created_at)`
- `ledger_entry(id, flat_id, entry_date, type ('BILL','LATE_FEE','PAYMENT','REVERSAL'), debit, credit, ref_type, ref_id, description, created_at, created_by)`; never updated or deleted.

Pure classes (no Spring):
- `BillCalculator.amount(areaSqft, rate)` → `BigDecimal` scale 2 HALF_UP.
- `LateFeeCalculator.interest(principalOutstanding, annualPct, fromExclusive, toInclusive)` = principal × pct/100 × days/365, scale 2 HALF_UP; zero when not overdue.

Services:
- `BillingRunService.run(YearMonth)`: for every flat, create the month's bill if absent (idempotent), post a BILL ledger debit, and post LATE_FEE charges for interest accrued on older overdue bills since the last charge (A5). Running it twice changes nothing.
- `FlatStatementService`: outstanding, advance, accrued (not yet posted) late fee, oldest unpaid due date, days overdue, ledger with running balance.
- APIs: `GET /api/flats/{flatId}/summary`, `GET /api/flats/{flatId}/bills`, `GET /api/flats/{flatId}/ledger` (resident member or HELPDESK). `POST /api/desk/billing-runs {month}` (HELPDESK).

Seed: three months of bills for every flat (ending with the current month). Payment history is added in Step 5.
Tests: 1150 × 6.50 = 7,475.00; 980 × 6.50 = 6,370.00; 1640 × 6.50 = 10,660.00; late fee on ₹6,370 for 47 days at 21% = 6370 × 0.21 × 47/365 = 172.25 (assert exact); zero interest on or before due date; billing run idempotent; ledger never updated (repository exposes no update/delete).
Commit: `feat(billing): generate monthly bills by area and post late interest to an append-only ledger`

### [x] Step 5: Payments with test-mode gateway and receipts
Flyway `V3__payments.sql`:
- `payment(id, flat_id, amount, method ('ONLINE_TEST','UPI','BANK_TRANSFER','CHEQUE'), source ('GATEWAY','BANK_STATEMENT','MANUAL'), gateway_ref, utr, paid_on, receipt_no UNIQUE, posted_by, created_at)`
- `payment_allocation(id, payment_id, bill_id NULL, late_fee_charge_id NULL, amount)`
- `payment_intent(id, flat_id, amount, idempotency_key UNIQUE, status ('CREATED','SUCCEEDED','FAILED'), gateway_ref UNIQUE, created_by, created_at, completed_at)`

Pure class `PaymentAllocator.allocate(amount, openItems)` → oldest bill principal first, then late-fee charges, remainder as advance (A6).
`PaymentPostingService.post(flatId, amount, method, source, refs, actor)`: one transaction: allocate, update `bill.amount_paid`, write PAYMENT ledger credit, generate receipt `RCPT-YYYYMM-NNNNN`, audit log. This is the **only** place money is posted. Matching (Step 8) calls it too.

Mock gateway (test mode):
- `POST /api/flats/{flatId}/payment-intents {amount, idempotencyKey}` → intent with `gateway_ref = PGTEST-<random>`. Same key returns the same intent. Amount must be > 0 and ≤ outstanding + ₹1,00,000 (sanity cap).
- `POST /api/payment-intents/{id}/simulate {outcome: SUCCESS|FAILURE}` → SUCCESS posts the payment exactly once even if called twice; FAILURE posts nothing.
- `GET /api/receipts/{receiptNo}` (member of that flat or HELPDESK).

Seed: payment history for the 3 months so roughly 70% of bills were paid by the due date (brief baseline), some late, some partial, a few unpaid. Force the three brief flats: Neeraj fully paid, current; Swati nothing paid, oldest unpaid due date exactly 47 days before today; Aamir paid ₹5,000 of ₹10,660, oldest unpaid due date exactly 18 days before today. To make 47 and 18 exact, seed those two flats' due dates explicitly from the clock instead of the normal 10th rule, and log this in PROGRESS.
Tests: Aamir pays 5,000 → 5,660.00 outstanding; overpayment becomes advance and is consumed by the next bill; double `simulate SUCCESS` posts once; same idempotency key returns the same intent; resident cannot pay or read receipts for another flat.
Commit: `feat(payments): add test-mode payments with oldest-first allocation and receipts`

### [x] Step 6: Bank statement CSV parser
Pure class `BankStatementParser.parse(InputStream)` → `ParsedStatement(rows, errors)`:
- Format per A8. Header aliases: date ← `Txn Date|Transaction Date|Date|Value Date`; description ← `Description|Narration|Particulars|Remarks`; reference ← `Ref No|Reference|Chq/Ref No|UTR|Cheque No`; credit ← `Credit|Deposit|Cr Amount|Credit Amount`; debit ← `Debit|Withdrawal|Dr Amount`. Case- and whitespace-insensitive, BOM tolerated.
- Amounts: strip `₹`, spaces and commas; must parse to scale-2 `BigDecimal`. Dates: `dd/MM/yyyy`, `dd-MM-yyyy`, `yyyy-MM-dd`.
- Extract `utr` (12-digit number or the reference column), `vpa` (regex `[a-z0-9._-]{2,}@[a-z]{2,}`, lowercased) and `gatewayRef` (`PGTEST-[A-Z0-9]+`) from description + reference.
- `rowHash` = SHA-256 of date | normalised description | reference | credit amount.
- Bad rows become per-row errors with the row number; they never fail the whole file. Missing required header → whole-file error with a clear message.
- Debit-only and zero rows are returned with `kind=NON_CREDIT`.
Create `samples/bank-statement-demo.csv` (about 20 rows, built against seed data) containing at least one row for **every** matching case in Step 7, plus: a debit row, a row with a malformed amount, Indian-grouped amounts, a BOM, and a gateway settlement row for a seeded online payment.
Tests: every alias set parses; Indian grouping; each date format; malformed row reported with its number while other rows parse; BOM file parses; missing header is a file error.
Commit: `feat(matching): parse bank statement CSVs into normalised credit rows`

### [x] Step 7: Matching engine (the hard part)
Pure class `PaymentMatcher.match(row, context)` → `MatchResult(decision, flatId, candidates, confidence, reasons[], duplicateOfRowNo)`. `context` is built once per import (not per row): flat codes → flat id, known UPI IDs → flat id, outstanding per flat and remaining principal per unpaid bill, payments by gateway ref, UTRs and gateway refs already posted or reconciled, and row hashes of earlier bank rows. Rows of the same file are added to the seen sets as they are matched, so a repeat inside one file is caught too.

Signals:
- **R (reference):** flat codes found in description/reference after normalisation. Accept `T07-1203`, `T7-1203`, `T07/1203`, `T07 1203`, `T071203`, `TOWER 7 FLAT 1203`. Must exist. Two different flats found → R is ambiguous.
- **V (UPI ID):** extracted VPA that is known for a flat.
- **A (amount):** flats whose total outstanding, or the remaining principal of one bill, equals the row amount exactly. Unique (one flat) or multiple. **A is a supporting signal only (A9):** it can confirm R or V, or suggest a flat on its own, but it never causes a conflict.
- **G (gateway):** gateway ref matching an existing gateway payment.

Decision table, applied in this order:
| # | Condition | Decision | Posted? |
|---|---|---|---|
| 1 | UTR (or gateway ref) already posted, reconciled or earlier in this import | `DUPLICATE` | No, and not queued |
| 2 | Not a credit | `NON_CREDIT` | No |
| 3 | Same row hash as an earlier bank row and no UTR | `POSSIBLE_DUPLICATE` (links the earlier row) | No, goes to the accounts queue (A28) |
| 4 | G matches and amount equal | `RECONCILED_GATEWAY` (link to existing payment) | No (already posted) |
| 5 | G matches, amount differs | `CONFLICT` | No |
| 6 | R is ambiguous, or R and V name different flats | `CONFLICT` with candidates | No |
| 7 | R and V name the same flat | `AUTO_POSTED`, confidence HIGH | **Yes** |
| 8 | Only one of R or V names a flat, and A is unique for that same flat | `AUTO_POSTED`, confidence HIGH | **Yes** |
| 9 | Only one of R or V names a flat (A absent, elsewhere, or on several flats) | `SUGGESTED`, confidence MEDIUM | No, one-click confirm |
| 10 | No R or V; A unique | `SUGGESTED`, confidence MEDIUM | No, one-click confirm |
| 11 | No R or V; A on several flats | `UNMATCHED` with candidates, confidence LOW | No |
| 12 | Nothing matches | `UNMATCHED` | No |

When A points to a different flat than R or V, it is ignored for the decision and a reason says so, e.g. "Amount ₹4,070.00 also equals T12-1108's bill; ignored because reference and UPI ID agree". An amount that matches several flats, even if one of them is the R/V flat, does not count as agreement. Every result carries human-readable reasons, e.g. "Reference T21-1601 found", "UPI aamir.s@okicici is known for T21-1601", "Amount ₹5,660.00 equals the remaining amount of T21-1601's October 2026 bill".

Tests: one test per table row, plus: reference with lowercase and spaces; every accepted reference format; VPA known for flat A but reference says flat B → CONFLICT; R + V agree while A points elsewhere → AUTO_POSTED with the "ignored" reason; R alone with A elsewhere → SUGGESTED with the "ignored" reason; V alone → SUGGESTED; R + unique A on the same flat → AUTO_POSTED; V + unique A on the same flat → AUTO_POSTED; R + A on several flats including the R flat → SUGGESTED; partial payment with reference + known VPA → AUTO_POSTED; amount equal for two flats → UNMATCHED with both candidates; overpayment with two agreeing signals → AUTO_POSTED; UTR repeated inside one file → DUPLICATE; same hash without UTR → POSSIBLE_DUPLICATE; and a performance test: 3,000 generated rows against 2,400 flats match in under 3 seconds.
Commit: `feat(matching): decide bank row matches from reference, UPI ID and amount`

### [x] Step 8: Bank import and the accounts queue
Flyway `V4__bank_matching.sql`:
- `bank_import(id, file_name, file_sha256, uploaded_by, uploaded_at, total_rows, auto_posted, reconciled, suggested, conflicts, unmatched, duplicates, possible_duplicates, non_credit, errors)`
- `bank_row(id, import_id, row_no, txn_date, description, reference, utr, vpa, gateway_ref, amount, row_hash (non-unique index), duplicate_of_row_id NULL FK bank_row, decision, suggested_flat_id, candidates JSONB, confidence, reasons JSONB, payment_id, resolved_by, resolved_at, resolution_note, parse_error)`. A row hash can repeat (two genuine identical transfers on one day); `duplicate_of_row_id` links a `POSSIBLE_DUPLICATE` to the row it repeats.
- `known_vpa(id, vpa UNIQUE, flat_id, learned_from_row_id, created_at)`. Seed known VPAs for about a third of flats, including `aamir.s@okicici` → T21-1601.

`BankImportService.importFile(file, actor)` (HELPDESK only; multipart; reject > 5 MB or non-CSV): parse → build context once → match each row → persist → auto-post via `PaymentPostingService` (source BANK_STATEMENT, method UPI/BANK_TRANSFER) → audit. The whole import is one transaction; a crash posts nothing.

Accounts queue (rows in SUGGESTED, CONFLICT, UNMATCHED, POSSIBLE_DUPLICATE; a possible duplicate shows the row it repeats, and "Not maintenance" with reason "duplicate" closes it without posting):
- `GET /api/desk/bank-imports`, `GET /api/desk/bank-imports/{id}` (summary + rows), `GET /api/desk/accounts-queue` (oldest first, with reasons and candidates).
- `POST /api/desk/bank-rows/{id}/confirm` (accept suggestion), `POST /api/desk/bank-rows/{id}/assign {flatId, note}`, `POST /api/desk/bank-rows/{id}/ignore {reason}` (not a maintenance payment). Each posts at most once (409 if already resolved), records who and when, audits, and on confirm/assign learns the VPA (A10) unless it belongs to another flat (then warn, do not overwrite).
- `GET /api/desk/flats/search?q=` for the assign picker (flat code or resident name).
**Back-dated payments and late interest:** a bank row can be dated before the last day already charged with late interest (statement imported after a billing run). When such a payment is posted (auto, confirm or assign), recompute each affected bill's posted late-fee periods with the payment at its real date (A24) and post the difference as a REVERSAL ledger credit linked to the late-fee charge ("Late interest adjusted for payment of <date>"). The charge's payable amount is reduced by its adjustments, so it is never collected twice. Test: a payment dated 20 Sep posted after the October run reduces the interest charged for 11–30 Sep to the correct amount, and outstanding equals bills + fees − adjustments − payments.

**Demo statement for reviewers (demo mode only):** `GET /api/desk/demo/bank-statement.csv` (HELPDESK) generates a statement from current data with one row per matching case (duplicate UTR, possible duplicate without UTR, debit, gateway reconcile, gateway amount conflict, signal conflict, ambiguous reference, two-signal auto-post, one-signal suggestion, amount on several flats, nothing matches, plus a malformed row), so a reviewer can download, upload and see every decision type on any date. Amount-only rows use one bill's remaining principal, never a total including late fees. The controller exists only when `APP_DEMO_MODE=true`; a test checks it is a 404 otherwise.

Tests (integration): importing the demo CSV gives the expected counts per decision; importing it again posts nothing new, reports rows with a UTR as duplicates and queues rows without a UTR as possible duplicates; no row in SUGGESTED/CONFLICT/UNMATCHED/POSSIBLE_DUPLICATE ever creates a payment; confirm posts exactly once and learns the VPA; assign to the wrong-but-chosen flat works and is audited; residents get 403 on all of these; the generated demo statement produces every decision type.
Commit: `feat(matching): import bank statements, auto-post confident matches and queue the rest`

### [x] Step 9: Complaints
Flyway `V5__complaints.sql`:
- `complaint(id, ref_no UNIQUE 'CMP-YYMM-NNNN', flat_id, raised_by, description, photo BYTEA NULL, photo_content_type, category, urgency, ai_category, ai_urgency, ai_confidence, ai_summary, classified_by ('AI','RULES_FALLBACK'), needs_review, safety_escalated, safety_terms TEXT[], status, assignee_id, parent_id NULL, created_at, first_response_at, first_response_due_at, resolve_due_at, resolved_at, closed_at, reopen_count, rating, rating_comment)`
- `complaint_event(id, complaint_id, type, actor_user_id, from_value, to_value, note, created_at)`
- `escalation(id, complaint_id, duty_manager_id, reason, created_at, acknowledged_by, acknowledged_at)`

Behaviour:
- Resident `POST /api/flats/{flatId}/complaints` (multipart: description 10–1,000 chars, optional photo ≤ 5 MB, JPEG/PNG/WebP checked by magic bytes). In this step use a stub classifier (category OTHER, urgency MEDIUM); Step 10 replaces it.
- Auto-assign per A16; set SLA due times per A14 from `society_settings`.
- State machine (A15) in a pure class `ComplaintStateMachine`; illegal transitions → 409. First staff action sets `first_response_at`.
- Resident: list own flat's complaints, detail with timeline, `close {rating 1–5, comment}` and `reopen {reason}` only from RESOLVED. Reopen increments `reopen_count` and returns to the same assignee.
- Help desk: `GET /api/desk/complaints?lane=&status=&category=`, detail, `PATCH status`, `PATCH assignee`, `POST comment`.
- Photos served by `GET /api/complaints/{id}/photo` with the same access check.
- Seed about 30 complaints across states and categories, including Swati's open **water leak** (T12-0408), resolved-and-rated ones, two reopened ones, and data for medians. (Lift duplicates and the gas escalation are seeded in Steps 10 and 11.)
Tests: every legal and illegal transition; reopen counts; rating only on close; resident 403 on other flats' complaints and photos; oversized or non-image photo rejected.
Commit: `feat(complaints): raise, assign, track and close complaints with response timers`

### [x] Step 10: AI classification, safety rule and review lane
- `SafetyRule.check(text)` (pure): configurable term list per A17, Unicode-aware, case-insensitive, word boundaries; returns matched terms. Runs **before** and **independently of** the AI.
- `ComplaintClassifier` interface; `GeminiComplaintClassifier` calls Gemini `generateContent` with `responseMimeType: application/json` and a response schema `{category (enum), urgency (enum), confidence (0–1), summary (≤ 12 words), reason}`. Sends only the description (A18). 6 s timeout. Validates enums and confidence.
- `KeywordFallbackClassifier` (pure): keyword → category map, default urgency MEDIUM, confidence 0.5.
- `ClassificationService`: try AI → on timeout, error, invalid JSON or invalid enum use fallback and set `classified_by=RULES_FALLBACK`, `needs_review=true`; if AI confidence < threshold → `needs_review=true`. Then apply safety: if `SafetyRule` matched → urgency CRITICAL, `safety_escalated=true`, create `escalation` to the duty manager, write an outbox SMS, event `ESCALATED`. The AI result can never lower this.
- One-tap recategorise: `PATCH /api/desk/complaints/{id}/category {category, urgency?}` records `CATEGORY_CHANGED` with from/to and clears `needs_review`. If the new category changes ownership, re-run auto-assign.
- `POST /api/desk/escalations/{id}/acknowledge`.
- `samples/complaints-eval.csv`: 30 labelled complaint texts (English, Hinglish, a few Marathi/Hindi). `ClassifierEvalTest` (tagged `ai-eval`, excluded from normal builds, runs only when `GEMINI_API_KEY` is set) prints accuracy for category and urgency. Record the result in PROGRESS for the TDD.
- Seed: a "gas smell near the stairs" complaint (escalated), one low-confidence complaint in review, one fallback-classified one.
Tests (no network; mock the HTTP client): AI says LOW but text contains "gas leak" → CRITICAL and escalated; Hindi/Marathi safety terms escalate; "fireplace" does not match "fire" mid-word while "fire" does; AI timeout → fallback + needs_review; invalid enum → fallback; confidence 0.4 → needs_review; recategorise records the correction.
Commit: `feat(ai): classify complaints with Gemini, escalate safety words by rule and fall back safely`
(Optionally a second commit: `test(ai): add labelled complaint set and offline classifier evaluation`.)

### [x] Step 11: Duplicate linking
- Pure `DuplicateDetector.findParent(newComplaint, recentOpenComplaints)` per A19: same tower, same category, different flat, parent not CLOSED; LIFT within 24 h on tower + category alone; others within 72 h with token Jaccard similarity ≥ 0.35 (lowercased, punctuation and a small stopword list removed). Link to the earliest root (no chains).
- Linking sets `parent_id`, writes `LINKED` events on both, never changes status.
- `POST /api/desk/complaints/{id}/unlink`; `GET` detail shows "linked to CMP-… (n flats reported this)".
- Help desk status change on a parent offers `applyToLinked=true`; it applies only when a person sends it, and each child gets its own event. Never automatic.
- Seed: 6 lift complaints from different flats in T07 within a few hours, linked to the first.
Tests: lift complaints linked; different tower not linked; dissimilar plumbing text not linked; closed parent not linked; linking never changes status; unlink works.
Commit: `feat(complaints): link duplicate complaints across flats without closing them`

### [x] Step 12: Committee view API
`GET /api/committee/overview?month=YYYY-MM` (HELPDESK) returns:
- **Collections:** billed, late fees charged, collected, collection rate; **on-time rate** = bills fully paid by due date ÷ bills due (target line: 90%).
- **Reconciliation:** opening outstanding + billed + late fees − collections = closing outstanding, with a `balanced: true|false` flag (must be true).
- **Dues ageing** by oldest unpaid due date: not yet due, 1–30, 31–60, 61–90, 90+ days; count of flats and amount per bucket; total equals closing outstanding.
- **Payment matching:** last import summary, auto-post share, queue size, oldest unresolved row age.
- **Complaints:** opened, resolved, median first response, median resolution, share breaching first-response target, **reopen rate** (target line: under 8%), average rating, open by category, active safety escalations.
- `GET /api/committee/ageing/{bucket}` lists flats in a bucket.
- `GET /api/committee/report.csv?month=` monthly report for download.
Tests: on a hand-built dataset assert each number exactly; reconciliation balances; ageing total equals outstanding; resident 403.
Commit: `feat(committee): add monthly collections, dues ageing and complaint metrics`

### [x] Step 13: Frontend scaffold, design system and sign-in
- Vite + React + TypeScript + Tailwind + React Router + TanStack Query in `/frontend`. Dev server proxies `/api` to `localhost:8080`. One `api.ts` that sends the CSRF header and handles 401 (go to login) and problem-detail errors.
- **Load the `frontend-design` skill before writing any UI.** Design tokens in `tailwind.config`: warm off-white background, near-black text, one deep-green accent for primary actions, red only for overdue and safety, amber for needs-review. One sans-serif family with tabular figures. Shared components: Button, Card, Badge (status/urgency), Money (Indian grouping, tabular, right-aligned), EmptyState, ErrorState, Skeleton, Toast, Page layout.
- Login: phone field (+91 prefilled) → OTP screen with 6 boxes, resend timer, attempts message; in demo mode a quiet banner shows the OTP. Demo quick-pick list of the demo users on the login screen (demo mode only).
- Routing by role: RESIDENT → resident app; HELPDESK → desk.
- Vitest: Money formatting (₹1,23,456.00), phone validation.
Commit: `feat(web): scaffold frontend with design system and OTP sign-in`

### [x] Step 14: Resident screens
Bottom navigation: Home, Bills, Complaints.
- **Home:** greeting + flat code; **dues card** (amount, due date or "overdue by N days" in red, late fee so far, primary **Pay** button), **open complaints** (status chip, assignee, time since raised, linked/escalated badges), all reachable in two taps. No visitors card until visitors are built.
- **Bills:** current outstanding, list of bills with status, ledger with running balance, receipts.
- **Pay flow:** amount prefilled with outstanding (editable for partial), → test gateway screen clearly labelled TEST MODE with "Pay successfully" and "Simulate failure" → success receipt or failure state with retry. Idempotency key per attempt.
- **Complaints:** list; **New** (text area, optional photo from camera or gallery with client-side resize to ≤ 1600 px, submit) → detail showing AI category and urgency, "You can expect a first response by …" timer, timeline, linked info, close-with-rating and reopen-with-reason only when resolved.
- Every screen: loading skeleton, empty state with a helpful action, error state with retry.
Commit (two allowed): `feat(web): add resident home, bills and test payment flow` and `feat(web): add resident complaint screens with timeline and rating`

### [x] Step 15: Help desk screens
Desk layout: sidebar on desktop, tabs on mobile: Complaints, Payments, Committee.
- **Complaints board:** lanes in this order: **Safety escalations** (red, with acknowledge), **Needs review** (AI unsure or fallback), **Open by response deadline** (overdue first), Resolved awaiting resident. Filters by category, status, assignee, tower.
- **Complaint detail:** text and photo, AI suggestion with confidence and reason, **one-tap category chips** to change category, urgency selector, assignee picker, status buttons for legal transitions only, comment box, linked complaints with unlink, apply-to-linked checkbox.
- **Payments:** upload CSV (drag and drop + file picker, size and type checks) → import summary (counts per decision) → rows table with decision badges and reasons. **Accounts queue:** each row shows amount, date, description, reasons and candidates, with Confirm (when suggested), Assign to flat (searchable picker) and Not maintenance (reason required). Optimistic disable on click so nothing posts twice. In demo mode only, a "Download sample statement" button next to the upload area fetches the generated demo statement (Step 8).
Commit (two allowed): `feat(web): add help desk complaint board with one-tap recategorise` and `feat(web): add bank statement import and accounts queue`

### [x] Step 16: Committee screen
Month picker; KPI row (collection rate vs 90% target, on-time rate, outstanding, reopen rate vs 8% target, median first response); reconciliation line with a balanced check; ageing bar with click-through to the flat list; complaint metrics by category; matching summary; Download CSV; print-friendly layout.
Commit: `feat(web): add committee dashboard with collections, ageing and complaint metrics`

### [x] Step 17: Polish and end-to-end check
- Walk every flow at 360 px and at desktop width. No horizontal scroll, no dead buttons, every async action has loading and error states, all forms validate inline, focus states visible, colour contrast AA.
- Security pass: no secrets in history (`git log -p | grep -iE "key|secret|password"` reviewed), CSRF working, every desk endpoint role-checked, every flat endpoint membership-checked, upload limits enforced, security headers set.
- Write `docs/DEMO.md`: a 5-minute reviewer walkthrough (resident pays → help desk imports CSV → queue → complaint with escalation → committee).
- **Demo reset:** `POST /api/desk/demo/reset` (HELPDESK only) that exists only when `APP_DEMO_MODE=true` (the controller bean is not created otherwise, so the route is a 404). It wipes all data (Flyway `clean` then `migrate`, allowed only in demo mode, because the ledger trigger correctly refuses deletes) and reseeds relative to the current date, so the brief scenarios (47 and 18 days overdue) are true again on any day. The desk UI has a "Reset demo data" button, shown only in demo mode, with a confirm step. Tests: reset restores the seeded state; resident gets 403; with demo mode off the endpoint does not exist.
Commit: `chore: polish states and accessibility and add reviewer demo script`

### [ ] Step 18: Docker image, deployment and docs
- Multi-stage `Dockerfile`: Node builds the frontend → Maven builds the jar with the frontend copied into `static/` → slim JRE 21 runtime, non-root user. Spring serves the SPA with a fallback to `index.html` for client routes.
- Deploy on Render (web service from Dockerfile, health check `/api/health`) with Neon PostgreSQL. Demo mode on in the deployed app.
- `deployment.md`: run locally step by step (Windows and macOS/Linux commands), how it is deployed, environment variable **names only**, demo logins (phones; OTP shown on screen), note on free-tier cold start.
- `README.md`: what it is, live link, screenshots, how to run, where the TDD lives.
Commit: `chore(deploy): add container build, Render deployment and deployment guide`

**Core is complete when Steps 0–18 are ticked and the live app passes `docs/DEMO.md`.**

---

## Part 2: Optional depth (only after Part 1 is complete)
Do these in order, one commit each, and stop when time runs out. Each must meet the same quality bar (tests, states, mobile).

- [ ] **Step 19: Hindi and Marathi** for resident screens (i18n with a language switcher; English complete; Hindi and Marathi for all resident strings). `feat(web): add Hindi and Marathi for resident screens`
- [ ] **Step 20: Installable web app**: manifest, icons, service worker caching the app shell; resident app installable on Android/iOS home screen. `feat(web): make the resident app installable`
- [ ] **Step 21: Gate visitors**: GUARD role, large-button tablet screen with photo capture, resident approve/deny from home ("today's visitors" card appears on resident home), pre-approved guest codes, gate privacy notice, visitor photos deleted after 90 days by a scheduled job (with a test), offline queue on the guard tablet that syncs when the network returns. `feat(visitors): add guard entry, resident approval, guest codes and 90-day photo deletion`
- [ ] **Step 22: Amenity booking**: clubhouse and courts, slots, rules (max per week, cancellation window), fees added to the flat ledger. `feat(amenities): add slot booking with rules and fees`
- [ ] **Step 23: Members and notices**: owner adds or removes family and tenant members; help desk posts notices shown on resident home. `feat(core): add family and tenant members and notices`

## Part 3: Submission
- [ ] **Step 24: TDD** in `docs/TDD.md` (converted to PDF for submission): GitHub URL and live link at the top; architecture and why; data model diagram; main flows; matching decision table; how the AI works and fails safely (with the eval accuracy); UI decisions with screenshots of key screens; assumptions table (from `docs/ASSUMPTIONS.md`); what was cut and what is next; honest limits. `docs: add technical design document`
- [ ] **Step 25: Final check**: fresh clone, follow `deployment.md` exactly on a clean machine, run all tests, run `docs/DEMO.md` on the live app, then zip TDD PDF + `deployment.md`.
