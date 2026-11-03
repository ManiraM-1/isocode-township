package com.isocode.township.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.isocode.township.IntegrationTest;
import com.isocode.township.billing.Bill;
import com.isocode.township.billing.BillRepository;
import com.isocode.township.billing.BillingRunService;
import com.isocode.township.billing.FlatStatementService;
import com.isocode.township.billing.LateFeeCharge;
import com.isocode.township.billing.LateFeeChargeRepository;
import com.isocode.township.billing.LedgerEntry;
import com.isocode.township.billing.OutstandingLateFees;
import com.isocode.township.billing.LedgerEntryRepository;
import com.isocode.township.billing.LedgerEntryType;
import com.isocode.township.billing.PaymentAllocation;
import com.isocode.township.billing.PaymentAllocationRepository;
import com.isocode.township.common.AuditLogRepository;
import com.isocode.township.common.BadRequestException;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.common.Money;
import com.isocode.township.flat.FlatRepository;
import com.isocode.township.payment.PaymentPostingService.PostedPayment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class PaymentPostingServiceTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    @Autowired
    private PaymentPostingService postingService;

    @Autowired
    private BillingRunService billingRunService;

    @Autowired
    private FlatStatementService statementService;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private LateFeeChargeRepository chargeRepository;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @Autowired
    private PaymentAllocationRepository allocationRepository;

    @Autowired
    private AuditLogRepository auditRepository;

    @Autowired
    private OutstandingLateFees outstandingLateFees;

    private Long neerajFlat;
    private Long swatiFlat;
    private Long aamirFlat;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        neerajFlat = flatId("T07-1203");
        swatiFlat = flatId("T12-0408");
        aamirFlat = flatId("T21-1601");
    }

    @Test
    void aamirPaysFiveThousandAndOwesFiftySixHundredSixty() {
        billingRunService.run(NOVEMBER, null);

        PostedPayment posted = pay(aamirFlat, "5000.00", LocalDate.of(2026, 11, 2));

        assertThat(statementService.summary(aamirFlat).outstanding()).isEqualTo(new BigDecimal("5660.00"));
        assertThat(billRepository.findByFlatIdOrderByPeriodDesc(aamirFlat).getFirst().getAmountPaid())
                .isEqualTo(new BigDecimal("5000.00"));
        assertThat(posted.plan().advance()).isEqualTo(Money.ZERO);
    }

    @Test
    void postingWritesPaymentAllocationLedgerCreditReceiptAndAudit() {
        billingRunService.run(NOVEMBER, null);

        Payment payment = pay(aamirFlat, "5000.00", LocalDate.of(2026, 11, 2)).payment();

        assertThat(payment.getReceiptNo()).matches("RCPT-202611-\\d{5}");
        assertThat(allocationRepository.findByPaymentIdOrderByIdAsc(payment.getId()))
                .extracting(PaymentAllocation::getAmount, PaymentAllocation::getAllocatedOn)
                .containsExactly(tuple(new BigDecimal("5000.00"), LocalDate.of(2026, 11, 2)));
        assertThat(ledgerRepository.findByFlatIdOrderByEntryDateAscIdAsc(aamirFlat))
                .filteredOn(entry -> entry.getType() == LedgerEntryType.PAYMENT)
                .extracting(LedgerEntry::getCredit, LedgerEntry::getEntryDate, LedgerEntry::getDescription)
                .containsExactly(tuple(new BigDecimal("5000.00"), LocalDate.of(2026, 11, 2),
                        "Payment received by UPI, receipt " + payment.getReceiptNo()));
        assertThat(auditRepository.findByEntityTypeAndEntityId("PAYMENT", payment.getId()))
                .singleElement()
                .satisfies(audit -> {
                    assertThat(audit.getAction()).isEqualTo("PAYMENT_POSTED");
                    assertThat(audit.getDetails()).containsEntry("receiptNo", payment.getReceiptNo());
                });
    }

    @Test
    void overpaymentBecomesAdvanceAndIsConsumedByTheNextBill() {
        billingRunService.run(OCTOBER, null);
        PostedPayment posted = pay(neerajFlat, "8000.00", LocalDate.of(2026, 10, 5));

        assertThat(posted.plan().advance()).isEqualTo(new BigDecimal("525.00"));
        assertThat(statementService.summary(neerajFlat).advance()).isEqualTo(new BigDecimal("525.00"));

        billingRunService.run(NOVEMBER, null);

        Bill november = billRepository.findByFlatIdOrderByPeriodDesc(neerajFlat).getFirst();
        assertThat(november.getAmountPaid()).isEqualTo(new BigDecimal("525.00"));
        assertThat(statementService.summary(neerajFlat).outstanding()).isEqualTo(new BigDecimal("6950.00"));
        assertThat(statementService.summary(neerajFlat).advance()).isEqualTo(Money.ZERO);
        assertThat(allocationRepository.findByPaymentIdOrderByIdAsc(posted.payment().getId()))
                .extracting(PaymentAllocation::getAmount, PaymentAllocation::getAllocatedOn)
                .containsExactly(
                        tuple(new BigDecimal("7475.00"), LocalDate.of(2026, 10, 5)),
                        tuple(new BigDecimal("525.00"), LocalDate.of(2026, 11, 1)));
    }

    @Test
    void latePartPaymentLowersInterestFromThePaymentDayOn() {
        billingRunService.run(SEPTEMBER, null);
        pay(swatiFlat, "3000.00", LocalDate.of(2026, 9, 20));

        billingRunService.run(OCTOBER, null);

        assertThat(chargeRepository.findByFlatIdOrderByToDateAscIdAsc(swatiFlat))
                .extracting(LateFeeCharge::getFromDate, LateFeeCharge::getToDate, LateFeeCharge::getAmount)
                .containsExactly(tuple(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 30), new BigDecimal("54.31")));
    }

    @Test
    void billPaidInFullLateStillOwesInterestForTheDaysItWasLate() {
        billingRunService.run(SEPTEMBER, null);
        pay(swatiFlat, "6370.00", LocalDate.of(2026, 9, 20));

        billingRunService.run(OCTOBER, null);
        billingRunService.run(NOVEMBER, null);

        assertThat(chargeRepository.findByFlatIdOrderByToDateAscIdAsc(swatiFlat))
                .extracting(LateFeeCharge::getAmount)
                .containsExactly(new BigDecimal("32.98"), new BigDecimal("76.96"));
    }

    @Test
    void principalOfEveryBillIsPaidBeforeAnyLateFee() {
        billingRunService.run(SEPTEMBER, null);
        billingRunService.run(OCTOBER, null);

        pay(swatiFlat, "6443.30", LocalDate.of(2026, 10, 2));

        assertThat(billRepository.findByFlatIdOrderByPeriodDesc(swatiFlat))
                .extracting(Bill::getMonth, Bill::getAmountPaid)
                .containsExactly(tuple(OCTOBER, new BigDecimal("73.30")), tuple(SEPTEMBER, new BigDecimal("6370.00")));
        assertThat(statementService.summary(swatiFlat).outstanding()).isEqualTo(new BigDecimal("6370.00"));
    }

    @Test
    void ledgerBalanceAlwaysEqualsBillsPlusFeesMinusPayments() {
        billingRunService.run(SEPTEMBER, null);
        pay(swatiFlat, "1000.00", LocalDate.of(2026, 9, 25));
        billingRunService.run(OCTOBER, null);
        pay(swatiFlat, "2500.50", LocalDate.of(2026, 10, 20));
        billingRunService.run(NOVEMBER, null);

        BigDecimal debits = billRepository.findByFlatIdOrderByPeriodDesc(swatiFlat).stream()
                .map(Bill::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(chargeRepository.findByFlatIdOrderByToDateAscIdAsc(swatiFlat).stream()
                        .map(LateFeeCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(ledgerRepository.balanceOf(swatiFlat)).isEqualByComparingTo(debits.subtract(new BigDecimal("3500.50")));
    }

    @Test
    void backDatedPaymentReversesTheInterestAlreadyChargedForDaysItCovers() {
        billingRunService.run(SEPTEMBER, null);
        billingRunService.run(OCTOBER, null);
        assertThat(chargeRepository.findByFlatIdOrderByToDateAscIdAsc(swatiFlat))
                .extracting(LateFeeCharge::getAmount).containsExactly(new BigDecimal("73.30"));

        pay(swatiFlat, "3000.00", LocalDate.of(2026, 9, 20));

        assertThat(ledgerRepository.findByFlatIdOrderByEntryDateAscIdAsc(swatiFlat))
                .filteredOn(entry -> entry.getType() == LedgerEntryType.REVERSAL)
                .extracting(LedgerEntry::getCredit, LedgerEntry::getEntryDate, LedgerEntry::getDescription)
                .containsExactly(tuple(new BigDecimal("18.99"), LocalDate.of(2026, 11, 3),
                        "Late interest adjusted for payment of 20 Sep 2026"));
        assertThat(statementService.summary(swatiFlat).outstanding())
                .isEqualTo(new BigDecimal("12740.00").add(new BigDecimal("73.30"))
                        .subtract(new BigDecimal("18.99")).subtract(new BigDecimal("3000.00")));
        assertThat(outstandingLateFees.forFlat(swatiFlat)).extracting(OutstandingLateFees.Balance::outstanding)
                .containsExactly(new BigDecimal("54.31"));
    }

    @Test
    void secondBackDatedPaymentReversesOnlyTheFurtherDifference() {
        billingRunService.run(SEPTEMBER, null);
        billingRunService.run(OCTOBER, null);
        pay(swatiFlat, "3000.00", LocalDate.of(2026, 9, 20));

        pay(swatiFlat, "1000.00", LocalDate.of(2026, 9, 25));

        assertThat(ledgerRepository.findByFlatIdOrderByEntryDateAscIdAsc(swatiFlat))
                .filteredOn(entry -> entry.getType() == LedgerEntryType.REVERSAL)
                .extracting(LedgerEntry::getCredit)
                .containsExactly(new BigDecimal("18.99"), new BigDecimal("3.45"));
        assertThat(outstandingLateFees.forFlat(swatiFlat)).extracting(OutstandingLateFees.Balance::outstanding)
                .containsExactly(new BigDecimal("50.86"));
    }

    @Test
    void paymentDatedAfterTheLastChargedDayReversesNothing() {
        billingRunService.run(SEPTEMBER, null);
        billingRunService.run(OCTOBER, null);

        pay(swatiFlat, "3000.00", LocalDate.of(2026, 10, 2));

        assertThat(ledgerRepository.findByFlatIdOrderByEntryDateAscIdAsc(swatiFlat))
                .extracting(LedgerEntry::getType).doesNotContain(LedgerEntryType.REVERSAL);
    }

    @Test
    void invalidPaymentsAreRejected() {
        assertThatThrownBy(() -> pay(swatiFlat, "0.00", LocalDate.of(2026, 11, 2)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> pay(swatiFlat, "10.005", LocalDate.of(2026, 11, 2)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> pay(swatiFlat, "10.00", LocalDate.of(2026, 11, 4)))
                .isInstanceOf(BadRequestException.class);
    }

    private PostedPayment pay(Long flatId, String amount, LocalDate paidOn) {
        return postingService.post(new PostPaymentCommand(flatId, new BigDecimal(amount), PaymentMethod.UPI,
                PaymentSource.MANUAL, null, null, paidOn, null));
    }

    private Long flatId(String code) {
        return flatRepository.findByCode(code).orElseThrow().getId();
    }
}
