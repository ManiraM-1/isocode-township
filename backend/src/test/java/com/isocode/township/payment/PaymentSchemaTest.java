package com.isocode.township.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.isocode.township.IntegrationTest;
import com.isocode.township.common.AuditLog;
import com.isocode.township.common.AuditLogRepository;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.common.Money;
import com.isocode.township.flat.FlatRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class PaymentSchemaTest {

    private static final Instant NOW = Instant.parse("2026-11-03T04:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AuditLogRepository auditRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long flatId;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        flatId = flatRepository.findByCode("T21-1601").orElseThrow().getId();
    }

    @Test
    void paymentsCannotBeEdited() {
        Long id = savePayment("RCPT-202611-90001", "PGTEST-AAA", null).getId();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE payment SET amount = 1 WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("payment is append-only");
    }

    @Test
    void paymentsCannotBeDeleted() {
        Long id = savePayment("RCPT-202611-90001", "PGTEST-AAA", null).getId();

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM payment WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("payment is append-only");
    }

    @Test
    void theSameGatewayReferenceCanBePostedOnlyOnce() {
        savePayment("RCPT-202611-90001", "PGTEST-AAA", null);

        assertThatThrownBy(() -> savePayment("RCPT-202611-90002", "PGTEST-AAA", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameBankUtrCanBePostedOnlyOnce() {
        savePayment("RCPT-202611-90001", null, "612345678901");

        assertThatThrownBy(() -> savePayment("RCPT-202611-90002", null, "612345678901"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allocationMustTargetExactlyOneBillOrLateFee() {
        Long paymentId = savePayment("RCPT-202611-90001", null, null).getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO payment_allocation (payment_id, amount, allocated_on) VALUES (?, 10.00, DATE '2026-11-03')",
                paymentId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void receiptSequenceIncreases() {
        long first = paymentRepository.nextReceiptSequence();

        assertThat(paymentRepository.nextReceiptSequence()).isEqualTo(first + 1);
    }

    @Test
    void finishedIntentMustHaveACompletionTime() {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE phone = '+919000000003'", Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO payment_intent (flat_id, amount, idempotency_key, status, gateway_ref, created_by, created_at)
                VALUES (?, 100.00, 'key-1', 'SUCCEEDED', 'PGTEST-X', ?, now())
                """, flatId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditDetailsAreStoredAsJson() {
        auditRepository.save(new AuditLog(null, "TEST", "PAYMENT", 1L, Map.of("receiptNo", "RCPT-1", "amount", "5.00"), NOW));
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT details->>'receiptNo' FROM audit_log WHERE action = 'TEST'", String.class))
                .isEqualTo("RCPT-1");
        assertThat(auditRepository.findByEntityTypeAndEntityId("PAYMENT", 1L).getFirst().getDetails())
                .containsEntry("amount", "5.00");
    }

    private Payment savePayment(String receiptNo, String gatewayRef, String utr) {
        Payment payment = paymentRepository.save(new Payment(flatId, Money.of("100.00"), PaymentMethod.UPI,
                PaymentSource.MANUAL, gatewayRef, utr, TODAY, receiptNo, null, NOW));
        entityManager.flush();
        return payment;
    }
}
