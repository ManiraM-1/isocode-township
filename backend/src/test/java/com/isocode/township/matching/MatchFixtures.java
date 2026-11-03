package com.isocode.township.matching;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

final class MatchFixtures {

    static final long AAMIR = 1;
    static final long SWATI = 2;
    static final long T12_1108 = 3;
    static final long T21_0104 = 4;
    static final long T21_1402 = 5;
    static final long T15_0702 = 6;
    static final long T07_1401 = 7;
    static final long T07_1407 = 8;
    static final long NEERAJ = 9;
    static final long T21_0303 = 10;
    static final long T15_1605 = 11;
    static final long T26_2007 = 12;
    static final long T26_1203 = 13;
    static final long T15_0801 = 14;
    static final long T15_0903 = 15;
    static final long T03_0904 = 16;

    static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    static final LocalDate STATEMENT_DAY = LocalDate.of(2026, 11, 2);

    private MatchFixtures() {
    }

    static MatchContext.Builder seedLikeContext() {
        return MatchContext.builder()
                .flat(AAMIR, "T21-1601").flat(SWATI, "T12-0408").flat(T12_1108, "T12-1108")
                .flat(T21_0104, "T21-0104").flat(T21_1402, "T21-1402").flat(T15_0702, "T15-0702")
                .flat(T07_1401, "T07-1401").flat(T07_1407, "T07-1407").flat(NEERAJ, "T07-1203")
                .flat(T21_0303, "T21-0303").flat(T15_1605, "T15-1605").flat(T26_2007, "T26-2007")
                .flat(T26_1203, "T26-1203").flat(T15_0801, "T15-0801").flat(T15_0903, "T15-0903")
                .flat(T03_0904, "T03-0904")
                .knownUpiId("aamir.s@okicici", AAMIR)
                .knownUpiId("kale.family@okaxis", T21_0303)
                .knownUpiId("dhole.home@okaxis", T26_2007)
                .knownUpiId("nene.home@okicici", T15_1605)
                .billRemaining(AAMIR, OCTOBER, money("5660.00")).billRemaining(AAMIR, NOVEMBER, money("10660.00"))
                .totalOutstanding(AAMIR, money("16368.85"))
                .billRemaining(SWATI, SEPTEMBER, money("6370.00")).billRemaining(SWATI, OCTOBER, money("6370.00"))
                .billRemaining(SWATI, NOVEMBER, money("6370.00")).totalOutstanding(SWATI, money("19348.21"))
                .billRemaining(T12_1108, OCTOBER, money("4070.00")).billRemaining(T12_1108, NOVEMBER, money("5135.00"))
                .billRemaining(T21_0104, NOVEMBER, money("6207.50"))
                .billRemaining(T21_1402, NOVEMBER, money("6207.50")).totalOutstanding(T21_1402, money("6207.50"))
                .billRemaining(T15_0702, OCTOBER, money("6760.00"))
                .billRemaining(T26_1203, OCTOBER, money("6190.00"))
                .billRemaining(T15_1605, NOVEMBER, money("7670.00"))
                .billRemaining(T15_0801, NOVEMBER, money("6727.50"))
                .billRemaining(T21_0303, SEPTEMBER, money("5687.50"))
                .monthlyBill(AAMIR, money("10660.00")).monthlyBill(SWATI, money("6370.00"))
                .monthlyBill(T26_1203, money("9945.00")).totalOutstanding(T26_1203, money("16259.34"))
                .monthlyBill(T21_0303, money("5687.50")).totalOutstanding(T21_0303, money("17298.11"))
                .monthlyBill(T15_1605, money("7670.00")).totalOutstanding(T15_1605, money("7670.00"))
                .gatewayPayment("PGTEST-JD9EP9VAU8CP", 501L, T15_0903, money("3000.00"), "RCPT-202611-00101")
                .gatewayPayment("PGTEST-SLAMTBLQNZXA", 502L, T03_0904, money("10432.50"), "RCPT-202611-00102")
                .postedUtr("699747290912", "RCPT-202611-00103");
    }

    static StatementRow credit(int rowNo, String description, String reference, String amount) {
        return row(rowNo, description, reference, money(amount), null);
    }

    static StatementRow debit(int rowNo, String description, String amount) {
        return row(rowNo, description, "", null, money(amount));
    }

    private static StatementRow row(int rowNo, String description, String reference, BigDecimal credit,
                                    BigDecimal debit) {
        RowKind kind = credit != null && credit.signum() > 0 ? RowKind.CREDIT : RowKind.NON_CREDIT;
        return new StatementRow(rowNo, STATEMENT_DAY, description, reference, credit, debit, kind,
                PaymentReferences.utr(description, reference),
                PaymentReferences.upiId(description, reference),
                PaymentReferences.gatewayRef(description, reference),
                BankStatementParser.rowHash(STATEMENT_DAY, description, reference, credit));
    }

    static BigDecimal money(String amount) {
        return Money.of(amount);
    }
}
