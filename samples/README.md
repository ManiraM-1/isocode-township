# Sample files

## `bank-statement-demo.csv`

A static statement for tests. It is built against the demo seed **on the fixed test clock, 3 November 2026** (`FixedClockConfiguration`), so every reference in it points at seeded data that exists on that date. On any other date, use the demo statement that the app generates from current data (`GET /api/desk/demo/bank-statement.csv`, Step 8).

Format (A8): UTF-8 with a byte order mark, header `Txn Date, Value Date, Description, Ref No, Debit, Credit, Balance`, dates `dd/MM/yyyy`, Indian-grouped amounts in quotes.

Amount-only matches always use **one bill's remaining principal**, never a total that includes late fees, so the expected result does not drift as interest accrues.

Known UPI IDs that Step 8 seeds for this file: `aamir.s@okicici` → T21-1601, `kale.family@okaxis` → T21-0303, `dhole.home@okaxis` → T26-2007, `nene.home@okicici` → T15-1605.

| Row | What it is | Signals | Expected decision (Step 7) |
|---|---|---|---|
| 2 | Electricity bill paid out, ₹1,25,640.00 | debit | `NON_CREDIT` |
| 3 | Aamir, ₹5,660.00, reference T21-1601, known UPI ID | R + V agree (A agrees too) | `AUTO_POSTED` |
| 4 | Swati, ₹6,370.00, reference `T12/0408` | R + unique A on the same flat | `AUTO_POSTED` |
| 5 | SMS charges, ₹59.00 | debit | `NON_CREDIT` |
| 6 | ₹6,190.00, lowercase reference `t26 1203` | R + unique A on the same flat | `AUTO_POSTED` |
| 7 | ₹3,000.00, `TOWER 15 FLAT 0702` | R only | `SUGGESTED` |
| 8 | ₹1,500.00 from a known UPI ID, no reference | V only | `SUGGESTED` |
| 9 | ₹4,070.00, the remaining principal of one T12-1108 bill | unique A only | `SUGGESTED` |
| 10 | ₹6,207.50, equal to one bill of T21-0104 and one of T21-1402 | A on two flats | `UNMATCHED` with both as candidates |
| 11 | ₹6,207.50, reference `T21 1402` | R; A on two flats including T21-1402 is not agreement | `SUGGESTED` |
| 12 | Aamir's UPI ID, reference says T07-1203 | R and V disagree | `CONFLICT` |
| 13 | ₹4,070.00, reference T21-0303, known UPI ID for T21-0303 | R + V agree; A points to T12-1108 and is ignored with a reason | `AUTO_POSTED` |
| 14 | One payment naming T07-1401 and T07-1407 | R ambiguous | `CONFLICT` |
| 15 | Gateway settlement `PGTEST-JD9EP9VAU8CP`, ₹3,000.00 | G, amount equal | `RECONCILED_GATEWAY` |
| 16 | Gateway settlement `PGTEST-SLAMTBLQNZXA`, ₹10,412.50 (seeded ₹10,432.50) | G, amount differs | `CONFLICT` |
| 17 | Neeraj, UTR 699747290912 already posted | UTR already posted | `DUPLICATE` |
| 18 | ₹10,000.00, reference T15-1605, known UPI ID, owes ₹7,670.00 | R + V agree, overpayment | `AUTO_POSTED` (₹2,330.00 advance) |
| 19 | Cash deposit ₹3,500.00, reference T15-0801, no UTR | R only | `SUGGESTED` |
| 20 | Exact repeat of row 19, still no UTR | same row hash, no UTR | `POSSIBLE_DUPLICATE` of row 19 (queued, never auto-posted) |
| 21 | Caterer refund, ₹1,23,450.00 | none | `UNMATCHED` |
| 22 | Exact repeat of row 21 with the same reference | UTR repeated inside the file | `DUPLICATE` |
| 23 | Aamir, ₹70,000.00, reference T21-1601, known UPI ID; dues ₹16,368.85, one bill ₹10,660.00 | R + V agree, but the amount is above dues plus one month's bill (A29) | `SUGGESTED` ("check before posting") |
| 24 | Amount `11,537.5O` (letter O) | — | row error, file still imports |

Totals: 5 auto-posted, 1 reconciled, 6 suggested, 3 conflicts, 2 unmatched, 2 duplicates, 1 possible duplicate, 2 non-credit, 1 error (23 rows).

`SampleStatementTest` checks that every seeded value this file relies on exists and that the parser reads it as described.

## `complaints-eval.csv`

Thirty-five labelled complaint texts for measuring the classifier (Step 10): 19 English, 6 Hinglish, 6 Hindi, 4 Marathi. Columns: `language, category, urgency, text`. All texts are invented. The seven Critical rows are emergencies the safety rule must catch whatever the AI says; five rows contain look-alikes of safety words (आगे, fireplace, gasket, "parked in front") that must not escalate.

- `KeywordBaselineEvalTest` runs in every build with the AI switched off and checks that the keyword fallback plus the safety rule miss no Critical row and raise no false alarm.
- `ClassifierEvalTest` calls Gemini and is excluded from normal builds. Run it with a key:

```
cd backend
GEMINI_API_KEY=... ./mvnw test -Dtest=ClassifierEvalTest -DexcludedGroups= -Dsurefire.failIfNoSpecifiedTests=false
```

It prints category and urgency accuracy, accuracy per language and every miss.
