# Five-minute reviewer walkthrough

The app runs in demo mode: sign-in codes are shown on screen, and the login page lists the demo accounts. Pick one, and the code is filled in for you to type. All data is fictional. Seed history is relative to today, so the scenarios below are true whenever you open the app.

If someone has already clicked through, sign in as Anjali and use **Reset demo data** (desk sidebar, or the top bar on a phone) to start fresh. It takes a few seconds.

Use a phone, or a desktop browser narrowed to phone width, for the resident part.

---

## 1. Resident pays a bill (1 minute)

1. On the login screen, choose **Aamir Shaikh · T21-1601**. Type the 6-digit code shown in the green banner.
2. **Home** shows the dues card: the outstanding amount, "Overdue by 18 days" in red, and the late fee so far. His latest due bill was ₹10,660 and he paid ₹5,000 of it.
3. Tap **Pay**. The amount is prefilled with what he owes. Change it to a part payment if you like; the screen says the oldest bill is paid first.
4. Tap **Continue to payment**. The test gateway is clearly marked TEST MODE. Tap **Simulate failure** first: nothing is charged and **Try again** starts a fresh attempt.
5. Pay again and tap **Pay successfully**. The receipt shows what the money paid (bill principal first, then late fees) and any advance.
6. **Bills → Statement** shows the payment in the running-balance ledger, linked to its receipt.

## 2. Resident raises a safety complaint (1 minute)

1. Sign out, then sign in as **Swati Joshi · T12-0408** (47 days overdue, with an open leak complaint).
2. Tap **New complaint** and write, for example: *"There is a gas smell near the lift lobby on the 4th floor."* Optionally add a photo; it is resized in the browser.
3. Tap **Send to help desk**. The detail opens at once. The safety rule has already escalated it to the duty manager ("The duty manager has been alerted. If anyone is in danger, call 112."). The category shows **Sorting…** for a few seconds while the AI classifies it in the background; the rule never waits for the AI.
4. The detail shows who handles it, "You can expect a first response by …", and the timeline.

## 3. Help desk works the board (1 minute)

1. Sign in as **Anjali Deshmukh** (help desk).
2. **Safety escalations** (red) lists the gas complaints, including Swati's. Tap **Acknowledge** on one.
3. **Needs review** (amber) holds complaints the AI was unsure about, or that the keyword fallback classified. Open one and tap the right **category chip**: one tap saves it, and the correction is recorded in the timeline.
4. In **Open, by response deadline** (overdue first), open one of the tower 7 lift complaints: six flats reported the same lift, and they are **linked, not closed**. Open the first report (the one the others are linked to), tick **Apply to linked complaints too** and tap **Mark resolved**. The card reports which linked complaints moved and which were skipped, and why.

## 4. Help desk matches a bank statement (1 minute)

1. Go to **Payments** and tap **Download sample statement**. The file is generated from today's data, with one row of each kind.
2. Drag the file onto the upload box, or use **Choose file**. The import page shows counts per decision: posted, reconciled (a test-gateway payment already received), suggested, conflict, unmatched, duplicate, possible duplicate, not a credit, unreadable. Tap a count to filter.
3. Unclear rows wait for you, each with its reasons and candidate flats:
   - **Confirm** a suggested row: it posts once and the UPI ID is learned.
   - **Assign to flat** on an unmatched row: search by flat code or resident name.
   - **Not maintenance** needs a reason.
   Buttons lock on the first click, so a double tap never posts twice.
4. Upload the **same file again**: everything comes back as duplicates and nothing is posted twice.

## 5. Committee view (1 minute)

1. Go to **Committee** and choose the previous month in the month picker.
2. The KPI row shows the collection rate, the share of bills paid on time against the 90% target, outstanding, the reopen rate against the "under 8%" target, and the median first response.
3. **Reconciliation** shows opening + billed + late fees − reversals − collections = closing, with **Balanced with the ledger**.
4. **Dues ageing** adds up to the outstanding total. Tap **31–60 days** to see the flats, most overdue first.
5. **Download CSV** gives the monthly report; **Print** gives a clean printout.

---

### Rules you can check while clicking

| Rule from the brief | Where to see it |
|---|---|
| Unclear payments are never posted automatically | Suggested, conflict and unmatched rows wait in the queue |
| Safety words escalate by rule, whatever the AI says | Step 2: escalated before the AI answers |
| Duplicate complaints are linked, never closed | Step 3: six lift complaints, each still owned by its resident |
| One-tap category change, recorded | Step 3: category chips, then the timeline entry |
| Residents see only their own flat | Each resident sees only their own flat; other flats' data returns 403 |
| Re-importing a statement never double-posts | Step 4, upload again |
