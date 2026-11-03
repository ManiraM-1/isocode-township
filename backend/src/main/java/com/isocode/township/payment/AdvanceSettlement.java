package com.isocode.township.payment;

import com.isocode.township.billing.BillsIssuedEvent;
import com.isocode.township.billing.PaymentAllocation;
import com.isocode.township.billing.PaymentAllocationRepository;
import com.isocode.township.payment.PaymentAllocator.AllocationPlan;
import java.math.BigDecimal;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdvanceSettlement {

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final OpenItemsQuery openItemsQuery;
    private final AllocationWriter allocationWriter;

    public AdvanceSettlement(PaymentRepository paymentRepository, PaymentAllocationRepository allocationRepository,
                             OpenItemsQuery openItemsQuery, AllocationWriter allocationWriter) {
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.openItemsQuery = openItemsQuery;
        this.allocationWriter = allocationWriter;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyAdvanceToNewBills(BillsIssuedEvent event) {
        for (Long flatId : event.flatIds()) {
            for (Payment payment : paymentRepository.findWithUnallocatedCredit(flatId)) {
                BigDecimal unallocated = payment.getAmount().subtract(allocatedSoFar(payment));
                if (unallocated.signum() <= 0) {
                    continue;
                }
                AllocationPlan plan = PaymentAllocator.allocate(unallocated, openItemsQuery.forFlat(flatId));
                if (plan.allocations().isEmpty()) {
                    break;
                }
                allocationWriter.write(payment.getId(), plan.allocations(), event.issuedOn());
            }
        }
    }

    private BigDecimal allocatedSoFar(Payment payment) {
        return allocationRepository.findByPaymentIdOrderByIdAsc(payment.getId()).stream()
                .map(PaymentAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
