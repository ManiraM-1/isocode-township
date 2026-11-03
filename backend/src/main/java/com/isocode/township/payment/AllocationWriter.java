package com.isocode.township.payment;

import com.isocode.township.billing.Bill;
import com.isocode.township.billing.BillRepository;
import com.isocode.township.billing.PaymentAllocation;
import com.isocode.township.billing.PaymentAllocationRepository;
import com.isocode.township.payment.PaymentAllocator.Allocation;
import com.isocode.township.payment.PaymentAllocator.ItemType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AllocationWriter {

    private final PaymentAllocationRepository allocationRepository;
    private final BillRepository billRepository;

    public AllocationWriter(PaymentAllocationRepository allocationRepository, BillRepository billRepository) {
        this.allocationRepository = allocationRepository;
        this.billRepository = billRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(Long paymentId, List<Allocation> allocations, LocalDate allocatedOn) {
        for (Allocation allocation : allocations) {
            Long itemId = allocation.item().id();
            if (allocation.item().type() == ItemType.BILL) {
                Bill bill = billRepository.findById(itemId).orElseThrow();
                bill.applyPayment(allocation.amount());
                allocationRepository.save(PaymentAllocation.toBill(paymentId, itemId, allocation.amount(), allocatedOn));
            } else {
                allocationRepository.save(PaymentAllocation.toLateFee(paymentId, itemId, allocation.amount(), allocatedOn));
            }
        }
    }
}
