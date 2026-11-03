package com.isocode.township.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutstandingLateFees {

    private final LateFeeChargeRepository chargeRepository;
    private final PaymentAllocationRepository allocationRepository;

    public OutstandingLateFees(LateFeeChargeRepository chargeRepository,
                               PaymentAllocationRepository allocationRepository) {
        this.chargeRepository = chargeRepository;
        this.allocationRepository = allocationRepository;
    }

    @Transactional(readOnly = true)
    public List<Balance> forFlat(Long flatId) {
        List<LateFeeCharge> charges = chargeRepository.findByFlatIdOrderByToDateAscIdAsc(flatId);
        if (charges.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> paidByCharge = allocationRepository
                .findByLateFeeChargeIdIn(charges.stream().map(LateFeeCharge::getId).toList()).stream()
                .collect(Collectors.groupingBy(PaymentAllocation::getLateFeeChargeId,
                        Collectors.reducing(BigDecimal.ZERO, PaymentAllocation::getAmount, BigDecimal::add)));
        return charges.stream()
                .map(charge -> new Balance(charge,
                        charge.getAmount().subtract(paidByCharge.getOrDefault(charge.getId(), BigDecimal.ZERO))))
                .filter(balance -> balance.outstanding().signum() > 0)
                .toList();
    }

    public record Balance(LateFeeCharge charge, BigDecimal outstanding) {
    }
}
