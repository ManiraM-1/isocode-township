package com.isocode.township.payment;

import com.isocode.township.billing.LedgerEntry;
import com.isocode.township.billing.LedgerEntryRepository;
import com.isocode.township.common.AuditService;
import com.isocode.township.common.BadRequestException;
import com.isocode.township.common.NotFoundException;
import com.isocode.township.flat.FlatRepository;
import com.isocode.township.payment.PaymentAllocator.AllocationPlan;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentPostingService {

    private static final int MONEY_SCALE = 2;

    private final FlatRepository flatRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final OpenItemsQuery openItemsQuery;
    private final AllocationWriter allocationWriter;
    private final ReceiptNumbers receiptNumbers;
    private final AuditService auditService;
    private final Clock clock;

    public PaymentPostingService(FlatRepository flatRepository, PaymentRepository paymentRepository,
                                 LedgerEntryRepository ledgerRepository, OpenItemsQuery openItemsQuery,
                                 AllocationWriter allocationWriter, ReceiptNumbers receiptNumbers,
                                 AuditService auditService, Clock clock) {
        this.flatRepository = flatRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerRepository = ledgerRepository;
        this.openItemsQuery = openItemsQuery;
        this.allocationWriter = allocationWriter;
        this.receiptNumbers = receiptNumbers;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public PostedPayment post(PostPaymentCommand command) {
        validate(command);
        flatRepository.findLockedById(command.flatId())
                .orElseThrow(() -> new NotFoundException("Flat not found"));
        Instant now = clock.instant();

        AllocationPlan plan = PaymentAllocator.allocate(command.amount(), openItemsQuery.forFlat(command.flatId()));
        String receiptNo = receiptNumbers.next(command.paidOn());
        Payment payment = paymentRepository.save(new Payment(command.flatId(), command.amount(), command.method(),
                command.source(), command.gatewayRef(), command.utr(), command.paidOn(), receiptNo,
                command.actorUserId(), now));
        allocationWriter.write(payment.getId(), plan.allocations(), command.paidOn());
        ledgerRepository.save(LedgerEntry.creditForPayment(command.flatId(), payment.getId(), command.paidOn(),
                command.amount(), PaymentDescriptions.ledgerCredit(command.method(), receiptNo), now,
                command.actorUserId()));
        auditService.record(command.actorUserId(), "PAYMENT_POSTED", "PAYMENT", payment.getId(),
                auditDetails(command, receiptNo, plan.advance()));

        return new PostedPayment(payment, plan);
    }

    private void validate(PostPaymentCommand command) {
        BigDecimal amount = command.amount();
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new BadRequestException("Amount must be a positive rupee amount with at most two decimals.");
        }
        if (command.paidOn() == null || command.paidOn().isAfter(LocalDate.now(clock))) {
            throw new BadRequestException("A payment cannot be dated in the future.");
        }
        if (command.method() == null || command.source() == null) {
            throw new BadRequestException("Payment method and source are required.");
        }
    }

    private static Map<String, Object> auditDetails(PostPaymentCommand command, String receiptNo, BigDecimal advance) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("flatId", command.flatId());
        details.put("amount", command.amount().toPlainString());
        details.put("method", command.method().name());
        details.put("source", command.source().name());
        details.put("paidOn", command.paidOn().toString());
        details.put("receiptNo", receiptNo);
        details.put("advance", advance.toPlainString());
        return details;
    }

    public record PostedPayment(Payment payment, AllocationPlan plan) {
    }
}
