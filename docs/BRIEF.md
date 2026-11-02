# Client brief (source of truth)

This file is the exact brief from Isocode Labs. Every feature, rule and number in this project must trace back to this file. Anything not stated here is an assumption and must be logged in `docs/ASSUMPTIONS.md`.

---

## Part A: Assignment rules from Isocode Labs

### Your task
A client has sent us the brief you pick below. Build the product, end to end, with a great UI and UX.

- You submit: One ZIP file with two files: your Technical Design Document (TDD) and a deployment.md
- Time: 7 days from when this step opens
- Work: Alone

Build a small part really well instead of the whole scope badly. The "CORE BUILD" paragraph in your problem statement says which slice must work end to end.

### Use AI, and use it well
AI tools are highly recommended. Use them to draft faster, test ideas, find gaps and polish your work, so that what you hand in is a more finished, specific outcome for this client and not a generic one. The judgement, the numbers and the final choices must still be yours. You will explain them in the interview.

### Must have
- The main flows in the brief work, on realistic sample data you create. No dead buttons
- Frontend, backend, a database and sign-in. Use roles if the brief has more than one kind of user
- The AI or automation part actually works, including what happens when it is wrong or unsure
- A polished UI: a consistent design, a layout that works on a phone, and loading, empty and error states
- Tests for the most important logic, and basic security: no secrets in the repository, validated inputs, access control

### How to submit
Put these two files in one .zip (up to 25 MB) and upload it.

1. Technical Design Document (PDF or Word)
   - The GitHub URL of your project, at the top. Also add the link to the live app if you deployed it
   - The architecture and why you chose it
   - The data model, the main flows and how the AI part works
   - UI and design decisions: the key screens and why they look the way they do
   - What you cut, and what you would do next
2. deployment.md
   - How to run it locally, step by step
   - How it is deployed, and the environment variables it needs (names only, never the values)
   - Demo login details for the reviewer

### Rules
- Use only the information in the problem statement. Label every assumption in your TDD
- Keep the commit history. We read it
- Do not put secrets or real personal data in the repository
- Keep the repository public until we have finished reviewing
- You can resubmit until the deadline; we review your latest version

### What great looks like
- A product a client could try today
- Clean, readable code with a clear structure
- A UI that feels designed, not default
- Honest notes about the limits
- Extra depth, well-judged features beyond the core and careful edge cases count in your favour

### Common mistakes
- A beautiful front end with nothing behind it
- Everything built, nothing working
- No sample data, so the app looks empty
- The AI part faked, or not handling mistakes
- A TDD with no GitHub URL

### How you'll be scored
| Area | Weight |
|---|---|
| Quality of work | 50% |
| Problem solving | 30% |
| Communication | 20% |

---

## Part B: Problem statement 4: Gated township's resident app for maintenance, complaints, and visitors

### Client
Company A, Pune. Facility management company running a gated township of 2,400 apartments in 28 towers for its residents' federation. 180 on-site staff, managing a maintenance budget of ₹22–26 crore a year.

### Today
Maintenance bills are made in Excel and emailed. Payments by cheque, transfer or UPI are matched by hand before entry in Tally. Complaints come by phone to the help desk and through 28 tower WhatsApp groups. Visitors are written in paper registers at 4 gates. The clubhouse and courts are booked on a shared spreadsheet.

### The problem
Assumptions: 2,400 flats × ₹7,500 a month = ₹21.6 crore billed a year. 3% is never collected: ₹64.8 lakh. 3 accounts staff spend 60% of their time matching payments: 3 × 60% × ₹4.8 lakh = ₹8.64 lakh. Of 1,800 complaints a month, 25% (450) are reopened, each costing 45 minutes at ₹250 an hour: 450 × 0.75 × ₹250 × 12 = ₹10.13 lakh. Total ₹83.57 lakh a year.

### Users
Residents (owners and tenants, mobile), help desk staff, and gate guards. The federation committee approves; the facility company's MD signs the deal.

### The ask
Residents should pay maintenance, raise complaints and book the clubhouse from one app, and always see where a complaint stands. Guards should log visitors on a tablet, and residents should approve guests from their phones. Payments must match themselves. The committee wants a monthly report it can trust.

### Must have
- Resident app (installable web app): OTP login linked to a flat, family and tenant members, notices
- Maintenance billing by flat area and rate, late fees per bye-laws, online payment (test mode), bank statement matching, receipts and a ledger per flat
- Complaints with category, photo, auto-assignment, response timer, status updates and a rating on closure
- Gate visitor management: guard tablet entry with photo, resident approval and pre-approved guest codes
- Amenity booking with slots, rules and fees
- Committee dashboard: collections, dues ageing and complaint times

### Core build
Software Engineering candidates must ship this slice end to end, in about 6 to 8 screens: resident login, maintenance bills with test payment, bank statement matching, complaints with assignment and status, and committee view. Roles: resident and help-desk staff. AI: complaint category and urgency. Hard part: payment matching from a bank CSV. Mock payments, messages and outside systems, and seed from the sample data. The rest of MUST HAVE is optional depth.

### Out of scope
- Accounting beyond maintenance (Tally stays)
- CCTV or boom-barrier hardware integration
- Classifieds or a marketplace

### AI or automation
Bank statement rows are matched to flats by amount, reference and UPI ID. AI classifies free-text complaints by category and urgency and links duplicates (ten flats reporting the same lift). When it is wrong: unclear payments go to an accounts queue and are never posted to a flat automatically; the help desk can change a category in one tap; safety words such as fire, gas, lift stuck or shock always escalate to the duty manager by rule, whatever the AI says; duplicates are linked, never closed.

### Constraints
Resident data, visitor photos and phone numbers are personal data under the DPDP Act 2023: delete visitor photos after 90 days and show a notice at gates. Billing and late interest follow the society's bye-laws under Maharashtra cooperative law. Gate tablets must keep working through network drops. English, Marathi and Hindi. Most bills are paid in the first 10 days of a month.

### Design
The client values good, minimal design: clean layouts, white space, a restrained palette and clear type, with nothing on screen that lacks a purpose. Design counts as much as features. Residents open the app for seconds at a time: the home screen shows dues, open complaints and today's visitors, with every action within two taps. Guard screens need very large buttons and photos that read well at night.

### Budget and time
₹9 lakh, 8 weeks. Success: on-time payments from 70% to 90% in 6 months; payment matching time down 80%; reopened complaints from 25% to under 8%.

### Sample data
| Flat | Resident | Area sq ft | Monthly Bill ₹ | Paid ₹ | Days Overdue | Open Complaints |
|---|---|---|---|---|---|---|
| T07-1203 | Neeraj Kulkarni | 1150 | 7475 | 7475 | 0 | 0 |
| T12-0408 | Swati Joshi (tenant) | 980 | 6370 | 0 | 47 | 1, water leak |
| T21-1601 | Aamir Shaikh | 1640 | 10660 | 5000 | 18 | 0 |

Rate used: ₹6.50 per sq ft per month.
