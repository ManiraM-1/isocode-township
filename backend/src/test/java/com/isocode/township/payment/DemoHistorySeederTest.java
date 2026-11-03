package com.isocode.township.payment;

import static com.isocode.township.FixedClockConfiguration.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.isocode.township.IntegrationTest;
import com.isocode.township.billing.BillStatus;
import com.isocode.township.billing.FlatStatementService;
import com.isocode.township.billing.LateFeeCharge;
import com.isocode.township.billing.LateFeeChargeRepository;
import com.isocode.township.billing.dto.BillResponse;
import com.isocode.township.billing.dto.FlatSummaryResponse;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class DemoHistorySeederTest {

    @Autowired
    private DemoDataSeeder coreSeeder;

    @Autowired
    private DemoHistorySeeder historySeeder;

    @Autowired
    private FlatStatementService statementService;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private LateFeeChargeRepository chargeRepository;

    @Autowired
    private PaymentIntentRepository intentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        coreSeeder.seedIfEmpty();
        historySeeder.seed();
    }

    @Test
    void neerajHasNothingOutstanding() {
        Long flat = flatId(DemoHistorySeeder.NEERAJ_FLAT);
        FlatSummaryResponse summary = statementService.summary(flat);

        assertThat(summary.outstanding()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.overdue()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.accruedLateFee()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.daysOverdue()).isZero();
        assertThat(statementService.bills(flat)).extracting(BillResponse::amount, BillResponse::status).containsOnly(
                tuple(new BigDecimal("7475.00"), BillStatus.PAID));
    }

    @Test
    void swatiHasPaidNothingAndHerOldestBillIsExactlyFortySevenDaysOverdue() {
        Long flat = flatId(DemoHistorySeeder.SWATI_FLAT);
        List<BillResponse> bills = statementService.bills(flat);
        BillResponse oldest = bills.getLast();

        assertThat(bills).extracting(BillResponse::amountPaid).containsOnly(new BigDecimal("0.00"));
        assertThat(oldest.amount()).isEqualTo(new BigDecimal("6370.00"));
        assertThat(oldest.balance()).isEqualTo(new BigDecimal("6370.00"));
        assertThat(oldest.dueDate()).isEqualTo(TODAY.minusDays(47));
        assertThat(ChronoUnit.DAYS.between(oldest.dueDate(), TODAY)).isEqualTo(47);
        assertThat(statementService.summary(flat).daysOverdue()).isEqualTo(47);
    }

    @Test
    void swatisLateFeesFollowTheNormalRules() {
        Long flat = flatId(DemoHistorySeeder.SWATI_FLAT);

        assertThat(chargeRepository.findByFlatIdOrderByToDateAscIdAsc(flat)).extracting(LateFeeCharge::getAmount)
                .containsExactly(new BigDecimal("47.64"), new BigDecimal("113.61"), new BigDecimal("76.96"));
        FlatSummaryResponse summary = statementService.summary(flat);
        assertThat(summary.outstanding()).isEqualTo(new BigDecimal("19348.21"));
        assertThat(summary.accruedLateFee()).isEqualTo(new BigDecimal("21.98"));
    }

    @Test
    void aamirOwesFiftySixHundredSixtyOnALatestDueBillExactlyEighteenDaysOverdue() {
        Long flat = flatId(DemoHistorySeeder.AAMIR_FLAT);
        List<BillResponse> bills = statementService.bills(flat);
        BillResponse partPaid = bills.stream().filter(bill -> bill.dueDate().isBefore(TODAY))
                .findFirst().orElseThrow();

        assertThat(partPaid.amount()).isEqualTo(new BigDecimal("10660.00"));
        assertThat(partPaid.amountPaid()).isEqualTo(new BigDecimal("5000.00"));
        assertThat(partPaid.balance()).isEqualTo(new BigDecimal("5660.00"));
        assertThat(partPaid.dueDate()).isEqualTo(TODAY.minusDays(18));
        assertThat(ChronoUnit.DAYS.between(partPaid.dueDate(), TODAY)).isEqualTo(18);
        assertThat(bills).filteredOn(bill -> bill.month().isBefore(partPaid.month()))
                .isNotEmpty()
                .extracting(BillResponse::status).containsOnly(BillStatus.PAID);
        assertThat(bills).filteredOn(bill -> bill.month().isAfter(partPaid.month()))
                .extracting(BillResponse::status, BillResponse::amountPaid)
                .containsOnly(tuple(BillStatus.DUE, new BigDecimal("0.00")));

        FlatSummaryResponse summary = statementService.summary(flat);
        assertThat(summary.daysOverdue()).isEqualTo(18);
        assertThat(summary.overdue()).isEqualTo(new BigDecimal("5708.85"));
        assertThat(summary.accruedLateFee()).isEqualTo(new BigDecimal("9.77"));
    }

    @Test
    void aboutSeventyPercentOfBillsThatFellDueWerePaidByTheDueDate() {
        Double onTimeShare = jdbcTemplate.queryForObject("""
                SELECT avg(CASE WHEN paid_by_due = amount THEN 1.0 ELSE 0.0 END) FROM (
                    SELECT b.amount,
                           COALESCE((SELECT sum(a.amount) FROM payment_allocation a
                                     WHERE a.bill_id = b.id AND a.allocated_on <= b.due_date), 0) AS paid_by_due
                    FROM bill b WHERE b.due_date < ?) AS due_bills
                """, Double.class, TODAY);

        assertThat(onTimeShare).isBetween(0.65, 0.75);
    }

    @Test
    void historyIncludesLatePartialAndUnpaidBills() {
        Integer paidLate = count("""
                SELECT count(*) FROM bill b WHERE b.amount_paid = b.amount
                AND EXISTS (SELECT 1 FROM payment_allocation a WHERE a.bill_id = b.id AND a.allocated_on > b.due_date)
                """);
        Integer partlyPaid = count("SELECT count(*) FROM bill WHERE amount_paid > 0 AND amount_paid < amount");
        Integer unpaidOverdue = count("SELECT count(*) FROM bill WHERE amount_paid = 0 AND due_date < DATE '2026-11-03'");

        assertThat(paidLate).isPositive();
        assertThat(partlyPaid).isPositive();
        assertThat(unpaidOverdue).isPositive();
    }

    @Test
    void onlinePaymentsHaveASucceededGatewayIntentForLaterStatementReconciliation() {
        Integer gatewayPayments = count("SELECT count(*) FROM payment WHERE source = 'GATEWAY'");
        Integer matchedIntents = count("""
                SELECT count(*) FROM payment p JOIN payment_intent i ON i.gateway_ref = p.gateway_ref
                WHERE p.source = 'GATEWAY' AND i.status = 'SUCCEEDED' AND i.amount = p.amount
                """);

        assertThat(gatewayPayments).isPositive().isEqualTo(matchedIntents);
        assertThat(intentRepository.count()).isEqualTo(gatewayPayments.longValue());
    }

    @Test
    void everyFlatLedgerBalancesAgainstItsBillsFeesAndPayments() {
        Integer unbalancedFlats = count("""
                SELECT count(*) FROM flat f WHERE
                  (SELECT COALESCE(sum(debit - credit), 0) FROM ledger_entry e WHERE e.flat_id = f.id)
                  <> (SELECT COALESCE(sum(amount), 0) FROM bill b WHERE b.flat_id = f.id)
                   + (SELECT COALESCE(sum(amount), 0) FROM late_fee_charge c WHERE c.flat_id = f.id)
                   - (SELECT COALESCE(sum(amount), 0) FROM payment p WHERE p.flat_id = f.id)
                """);

        assertThat(unbalancedFlats).isZero();
    }

    private Integer count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private Long flatId(String code) {
        return flatRepository.findByCode(code).orElseThrow().getId();
    }
}
