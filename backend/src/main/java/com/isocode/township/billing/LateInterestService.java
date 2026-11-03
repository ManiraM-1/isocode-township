package com.isocode.township.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LateInterestService {

    private final LateFeeChargeRepository chargeRepository;
    private final PaymentAllocationRepository allocationRepository;

    public LateInterestService(LateFeeChargeRepository chargeRepository,
                               PaymentAllocationRepository allocationRepository) {
        this.chargeRepository = chargeRepository;
        this.allocationRepository = allocationRepository;
    }

    @Transactional(readOnly = true)
    public List<BillInterest> unchargedInterest(List<Bill> bills, LocalDate upToInclusive, BigDecimal annualPct) {
        if (bills.isEmpty()) {
            return List.of();
        }
        List<Long> billIds = bills.stream().map(Bill::getId).toList();
        Map<Long, LocalDate> lastChargedDays = chargeRepository.lastChargedDayByBill(billIds);
        Map<Long, List<PrincipalReduction>> reductionsByBill = allocationRepository
                .findByBillIdInOrderByAllocatedOnAscIdAsc(billIds).stream()
                .collect(Collectors.groupingBy(PaymentAllocation::getBillId,
                        Collectors.mapping(allocation -> new PrincipalReduction(
                                allocation.getAllocatedOn(), allocation.getAmount()), Collectors.toList())));

        return bills.stream()
                .map(bill -> {
                    LocalDate startsAfter = LateFeeCalculator.interestStartsAfter(
                            bill.getDueDate(), lastChargedDays.get(bill.getId()));
                    BigDecimal amount = LateFeeCalculator.interestOnDecliningPrincipal(bill.getAmount(),
                            reductionsByBill.getOrDefault(bill.getId(), List.of()), annualPct, startsAfter, upToInclusive);
                    return new BillInterest(bill, startsAfter, amount);
                })
                .toList();
    }
}
