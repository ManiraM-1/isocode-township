package com.isocode.township.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.isocode.township.IntegrationTest;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.common.Money;
import com.isocode.township.flat.FlatRepository;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class BillingSchemaTest {

    private static final Instant NOW = Instant.parse("2026-11-03T04:30:00Z");

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long flatId;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        flatId = flatRepository.findByCode("T12-0408").orElseThrow().getId();
    }

    @Test
    void ledgerRowsCannotBeUpdated() {
        Long entryId = postBillWithLedgerEntry().getId();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE ledger_entry SET debit = 1 WHERE id = ?", entryId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void ledgerRowsCannotBeDeleted() {
        Long entryId = postBillWithLedgerEntry().getId();

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM ledger_entry WHERE id = ?", entryId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void ledgerTableCannotBeTruncated() {
        assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE ledger_entry"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void ledgerRepositoryOffersNoWayToUpdateOrDelete() {
        assertThat(CrudRepository.class.isAssignableFrom(LedgerEntryRepository.class)).isFalse();
        assertThat(Arrays.stream(LedgerEntryRepository.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("delete") || name.startsWith("update") || name.startsWith("remove"));
        assertThat(CrudRepository.class.isAssignableFrom(LateFeeChargeRepository.class)).isFalse();
    }

    @Test
    void ledgerEntryMustHaveExactlyOneSide() {
        Bill bill = saveBill(YearMonth.of(2026, 11));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO ledger_entry (flat_id, entry_date, type, debit, credit, ref_type, ref_id, description, created_at)
                VALUES (?, DATE '2026-11-01', 'BILL', 10.00, 10.00, 'BILL', ?, 'both sides', now())
                """, flatId, bill.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aFlatHasOnlyOneBillPerMonth() {
        saveBill(YearMonth.of(2026, 11));

        assertThatThrownBy(() -> saveBill(YearMonth.of(2026, 11)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void billCannotRecordMoreThanItsAmountAsPaid() {
        Bill bill = saveBill(YearMonth.of(2026, 11));

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE bill SET amount_paid = amount + 0.01 WHERE id = ?", bill.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ledgerBalanceIsDebitsMinusCredits() {
        postBillWithLedgerEntry();

        assertThat(ledgerRepository.balanceOf(flatId)).isEqualByComparingTo("6370.00");
        assertThat(ledgerRepository.balanceOf(-1L)).isEqualByComparingTo("0");
    }

    private LedgerEntry postBillWithLedgerEntry() {
        Bill bill = saveBill(YearMonth.of(2026, 11));
        LedgerEntry entry = ledgerRepository.save(LedgerEntry.debitForBill(bill, "Maintenance, November 2026", NOW, null));
        entityManager.flush();
        return entry;
    }

    private Bill saveBill(YearMonth month) {
        Bill bill = new Bill(flatId, month, 980, Money.of("6.50"), Money.of("6370.00"), month.atDay(10), NOW);
        return billRepository.saveAndFlush(bill);
    }
}
