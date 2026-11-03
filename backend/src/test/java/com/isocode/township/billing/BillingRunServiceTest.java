package com.isocode.township.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.isocode.township.IntegrationTest;
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.common.BadRequestException;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class BillingRunServiceTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    @Autowired
    private BillingRunService billingRunService;

    @Autowired
    private BillingDemoSeeder billingDemoSeeder;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private LateFeeChargeRepository chargeRepository;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long neerajFlat;
    private Long swatiFlat;
    private Long aamirFlat;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        neerajFlat = flatRepository.findByCode("T07-1203").orElseThrow().getId();
        swatiFlat = flatRepository.findByCode("T12-0408").orElseThrow().getId();
        aamirFlat = flatRepository.findByCode("T21-1601").orElseThrow().getId();
    }

    @Test
    void runBillsEveryFlatByAreaAndRateWithADueDateOnTheTenth() {
        BillingRunResult result = billingRunService.run(NOVEMBER, null);

        assertThat(result.billsCreated()).isEqualTo(48);
        assertThat(billRepository.findByFlatIdOrderByPeriodDesc(neerajFlat))
                .extracting(Bill::getAmount, Bill::getAreaSqft, Bill::getRate, Bill::getDueDate)
                .containsExactly(tuple(new BigDecimal("7475.00"), 1150, new BigDecimal("6.50"), LocalDate.of(2026, 11, 10)));
        assertThat(billRepository.findByFlatIdOrderByPeriodDesc(swatiFlat))
                .extracting(Bill::getAmount).containsExactly(new BigDecimal("6370.00"));
        assertThat(billRepository.findByFlatIdOrderByPeriodDesc(aamirFlat))
                .extracting(Bill::getAmount).containsExactly(new BigDecimal("10660.00"));
    }

    @Test
    void everyBillIsDebitedToTheFlatLedger() {
        billingRunService.run(NOVEMBER, null);

        assertThat(ledgerRepository.findByFlatIdOrderByEntryDateAscIdAsc(neerajFlat))
                .extracting(LedgerEntry::getType, LedgerEntry::getEntryDate, LedgerEntry::getDebit,
                        LedgerEntry::getCredit, LedgerEntry::getDescription)
                .containsExactly(tuple(LedgerEntryType.BILL, LocalDate.of(2026, 11, 1), new BigDecimal("7475.00"),
                        new BigDecimal("0.00"), "Maintenance for November 2026"));
        assertThat(ledgerRepository.count()).isEqualTo(48);
    }

    @Test
    void runningTheSameMonthTwiceChangesNothing() {
        runSeptemberToNovember();
        long bills = billRepository.count();
        long charges = chargeRepository.count();
        long ledgerEntries = ledgerRepository.count();

        BillingRunResult again = billingRunService.run(NOVEMBER, null);
        billingRunService.run(OCTOBER, null);

        assertThat(again.billsCreated()).isZero();
        assertThat(again.lateFeeChargesPosted()).isZero();
        assertThat(billRepository.count()).isEqualTo(bills);
        assertThat(chargeRepository.count()).isEqualTo(charges);
        assertThat(ledgerRepository.count()).isEqualTo(ledgerEntries);
    }

    @Test
    void lateInterestIsPostedMonthlyUpToTheEndOfThePreviousMonth() {
        BillingRunResult october = runSeptemberToNovember().get(1);

        assertThat(october.lateInterestPostedUpTo()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(chargeRepository.findByFlatIdOrderByToDateAscIdAsc(swatiFlat))
                .extracting(LateFeeCharge::getFromDate, LateFeeCharge::getToDate, LateFeeCharge::getAmount)
                .containsExactly(
                        tuple(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 30), new BigDecimal("73.30")),
                        tuple(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("113.61")),
                        tuple(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 31), new BigDecimal("76.96")));
        assertThat(ledgerRepository.findByFlatIdOrderByEntryDateAscIdAsc(swatiFlat))
                .filteredOn(entry -> entry.getType() == LedgerEntryType.LATE_FEE)
                .extracting(LedgerEntry::getDescription)
                .containsExactly(
                        "Late interest on September 2026 bill, 11 Sep to 30 Sep 2026",
                        "Late interest on September 2026 bill, 1 Oct to 31 Oct 2026",
                        "Late interest on October 2026 bill, 11 Oct to 31 Oct 2026");
    }

    @Test
    void theCurrentMonthsBillIsNotChargedInterestBeforeItIsDue() {
        runSeptemberToNovember();

        Long novemberBill = billRepository.findByFlatIdOrderByPeriodDesc(swatiFlat).getFirst().getId();
        assertThat(chargeRepository.findByFlatIdOrderByToDateAscIdAsc(swatiFlat))
                .extracting(LateFeeCharge::getBillId).doesNotContain(novemberBill);
    }

    @Test
    void fullyPaidBillsAccrueNoInterest() {
        billingRunService.run(SEPTEMBER, null);
        entityManager.flush();
        jdbcTemplate.update("UPDATE bill SET amount_paid = amount WHERE flat_id = ?", neerajFlat);
        entityManager.clear();

        billingRunService.run(OCTOBER, null);

        assertThat(chargeRepository.findByFlatIdOrderByToDateAscIdAsc(neerajFlat)).isEmpty();
        assertThat(chargeRepository.findByFlatIdOrderByToDateAscIdAsc(swatiFlat)).hasSize(1);
    }

    @Test
    void futureMonthsCannotBeBilled() {
        assertThatThrownBy(() -> billingRunService.run(YearMonth.of(2026, 12), null))
                .isInstanceOf(BadRequestException.class);
        assertThat(billRepository.count()).isZero();
    }

    @Test
    void ledgerRecordsWhoRanTheBilling() {
        Long anjali = userRepository.findByPhone("+919000000101").orElseThrow().getId();

        billingRunService.run(NOVEMBER, anjali);

        assertThat(ledgerRepository.findByFlatIdOrderByEntryDateAscIdAsc(swatiFlat))
                .extracting(LedgerEntry::getCreatedBy).containsOnly(anjali);
    }

    @Test
    void demoSeedBillsThreeMonthsEndingWithTheCurrentMonth() {
        billingDemoSeeder.seed();

        assertThat(billRepository.count()).isEqualTo(3 * 48);
        assertThat(billRepository.findByFlatIdOrderByPeriodDesc(aamirFlat)).extracting(Bill::getMonth)
                .containsExactly(NOVEMBER, OCTOBER, SEPTEMBER);
    }

    private List<BillingRunResult> runSeptemberToNovember() {
        return List.of(
                billingRunService.run(SEPTEMBER, null),
                billingRunService.run(OCTOBER, null),
                billingRunService.run(NOVEMBER, null));
    }
}
