package com.isocode.township.billing;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface PaymentAllocationRepository extends Repository<PaymentAllocation, Long> {

    <S extends PaymentAllocation> S save(S allocation);

    List<PaymentAllocation> findByPaymentIdOrderByIdAsc(Long paymentId);

    List<PaymentAllocation> findByBillIdInOrderByAllocatedOnAscIdAsc(Collection<Long> billIds);

    List<PaymentAllocation> findByLateFeeChargeIdIn(Collection<Long> chargeIds);
}
