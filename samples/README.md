# Sample bank statements

## `bank-statement-demo.csv`

A static statement for tests. It is built against the demo seed **on the fixed test clock, 3 November 2026** (`FixedClockConfiguration`), so every reference in it points at seeded data that exists on that date. On any other date, use the demo statement that the app generates from current data (`GET /api/desk/demo/bank-statement.csv`, Step 8).

Format (A8): UTF-8 with a byte order mark, header `Txn Date, Value Date, Description, Ref No, Debit, Credit, Balance`, dates `dd/MM/yyyy`, Indian-grouped amounts in quotes.

Amount-only matches always use **one bill's remaining principal**, never a total that includes late fees, so the expected result does not drift as interest accrues.

Known UPI IDs that Step 8 seeds for this file: `aamir.s@okicici` → T21-1601, `kale.family@okaxis` → T21-0303, `dhole.home@okaxis` → T26-2007, `nene.home@okicici` → T15-1605.

| Row | What it is | Signals | Expected decision (Step 7) |
|---|---|---|---|
| 2 | Electricity bill paid out, ₹1,25,640.00 | debit | `NON_CREDIT` |
| 3 | Aamir, ₹5,660.00, reference T21-1601, known UPI ID | R + V + A agree | `AUTO_POSTED` |
| 4 | Swati, ₹6,370.00, reference `T12/0408` | R + A agree | `AUTO_POSTED` |
| 5 | SMS charges, ₹59.00 | debit | `NON_CREDIT` |
| 6 | ₹6,190.00, lowercase reference `t26 1203` | R + A agree | `AUTO_POSTED` |
| 7 | ₹3,000.00, `TOWER 15 FLAT 0702` | R only | `SUGGESTED` |
| 8 | ₹1,500.00 from a known UPI ID, no reference | V only | `SUGGESTED` |
| 9 | ₹4,070.00, the remaining principal of one T12-1108 bill | A only | `SUGGESTED` |
| 10 | ₹6,207.50, equal to one bill of T21-0104 and one of T21-1402 | A on two flats | `UNMATCHED` with both as candidates |
| 11 | Aamir's UPI ID, reference says T07-1203 | V and R disagree | `CONFLICT` |
| 12 | ₹2,000.00 part payment, reference T21-0303, known UPI ID | R + V agree | `AUTO_POSTED` |
| 13 | One payment naming T07-1401 and T07-1407 | R ambiguous | `CONFLICT` |
| 14 | Gateway settlement `PGTEST-JD9EP9VAU8CP`, ₹3,000.00 | G, amount equal | `RECONCILED_GATEWAY` |
| 15 | Gateway settlement `PGTEST-SLAMTBLQNZXA`, ₹10,412.50 (seeded ₹10,432.50) | G, amount differs | `CONFLICT` |
| 16 | Neeraj, UTR 699747290912 already posted | UTR seen | `DUPLICATE` |
| 17 | ₹10,000.00, reference T15-1605, known UPI ID, owes ₹7,670.00 | R + V agree, overpayment | `AUTO_POSTED` (₹2,330.00 advance) |
| 18 | Caterer refund, ₹1,23,450.00 | none | `UNMATCHED` |
| 19 | Exact repeat of row 18 | row hash seen | `DUPLICATE` |
| 20 | Amount `11,537.5O` (letter O) | — | row error, file still imports |

Totals: 5 auto-posted, 1 reconciled, 3 suggested, 3 conflicts, 2 unmatched, 2 duplicates, 2 non-credit, 1 error (19 rows).

`SampleStatementTest` checks that every seeded value this file relies on exists and that the parser reads it as described.
