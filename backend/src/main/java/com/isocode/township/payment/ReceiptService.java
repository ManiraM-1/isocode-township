package com.isocode.township.payment;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.billing.LedgerEntry;
import com.isocode.township.billing.LedgerEntryRepository;
import com.isocode.township.billing.PaymentAllocation;
import com.isocode.township.billing.PaymentAllocationRepository;
import com.isocode.township.common.NotFoundException;
import com.isocode.township.flat.FlatAccessGuard;
import com.isocode.township.flat.FlatRepository;
import com.isocode.township.payment.dto.ReceiptResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptService {

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final FlatRepository flatRepository;
    private final FlatAccessGuard accessGuard;

    public ReceiptService(PaymentRepository paymentRepository, PaymentAllocationRepository allocationRepository,
                          LedgerEntryRepository ledgerRepository, FlatRepository flatRepository,
                          FlatAccessGuard accessGuard) {
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.ledgerRepository = ledgerRepository;
        this.flatRepository = flatRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public ReceiptResponse byNumber(SessionUser user, String receiptNo) {
        Payment payment = paymentRepository.findByReceiptNo(receiptNo)
                .orElseThrow(() -> new NotFoundException("Receipt not found"));
        accessGuard.requireMemberOrStaff(user, payment.getFlatId());

        List<ReceiptResponse.Line> lines = allocationRepository.findByPaymentIdOrderByIdAsc(payment.getId()).stream()
                .map(allocation -> new ReceiptResponse.Line(describe(allocation), allocation.getAmount(),
                        allocation.getAllocatedOn()))
                .toList();
        BigDecimal applied = lines.stream().map(ReceiptResponse.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReceiptResponse(
                payment.getReceiptNo(),
                payment.getFlatId(),
                flatRepository.findById(payment.getFlatId()).orElseThrow().getCode(),
                payment.getAmount(),
                payment.getPaidOn(),
                payment.getMethod(),
                payment.getSource(),
                payment.getGatewayRef().or(payment::getUtr).orElse(null),
                lines,
                payment.getAmount().subtract(applied),
                payment.getCreatedAt());
    }

    private String describe(PaymentAllocation allocation) {
        boolean toBill = allocation.getBillId() != null;
        String refType = toBill ? LedgerEntry.REF_BILL : LedgerEntry.REF_LATE_FEE_CHARGE;
        Long refId = toBill ? allocation.getBillId() : allocation.getLateFeeChargeId();
        return ledgerRepository.findFirstByRefTypeAndRefId(refType, refId)
                .map(LedgerEntry::getDescription)
                .orElse(toBill ? "Maintenance bill" : "Late interest");
    }
}
