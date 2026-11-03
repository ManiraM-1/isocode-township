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
    private final LateFeeAdjustmentRepository adjustmentRepository;

    public OutstandingLateFees(LateFeeChargeRepository chargeRepository,
                               PaymentAllocationRepository allocationRepository,
                               LateFeeAdjustmentRepository adjustmentRepository) {
        this.chargeRepository = chargeRepository;
        this.allocationRepository = allocationRepository;
        this.adjustmentRepository = adjustmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Balance> forFlat(Long flatId) {
        List<LateFeeCharge> charges = chargeRepository.findByFlatIdOrderByToDateAscIdAsc(flatId);
        if (charges.isEmpty()) {
            return List.of();
        }
        List<Long> chargeIds = charges.stream().map(LateFeeCharge::getId).toList();
        Map<Long, BigDecimal> paidByCharge = allocationRepository.findByLateFeeChargeIdIn(chargeIds).stream()
                .collect(Collectors.groupingBy(PaymentAllocation::getLateFeeChargeId,
                        Collectors.reducing(BigDecimal.ZERO, PaymentAllocation::getAmount, BigDecimal::add)));
        Map<Long, BigDecimal> adjustedByCharge = adjustmentRepository.findByLateFeeChargeIdIn(chargeIds).stream()
                .collect(Collectors.groupingBy(LateFeeAdjustment::getLateFeeChargeId,
                        Collectors.reducing(BigDecimal.ZERO, LateFeeAdjustment::getAmount, BigDecimal::add)));
        return charges.stream()
                .map(charge -> new Balance(charge, charge.getAmount()
                        .subtract(paidByCharge.getOrDefault(charge.getId(), BigDecimal.ZERO))
                        .subtract(adjustedByCharge.getOrDefault(charge.getId(), BigDecimal.ZERO))))
                .filter(balance -> balance.outstanding().signum() > 0)
                .toList();
    }

    public record Balance(LateFeeCharge charge, BigDecimal outstanding) {
    }
}
