package com.isocode.township.billing;

import com.isocode.township.common.Money;
import com.isocode.township.common.SocietySettingsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LateFeeCorrection {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final BillRepository billRepository;
    private final LateFeeChargeRepository chargeRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final LateFeeAdjustmentRepository adjustmentRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final SocietySettingsRepository settingsRepository;

    public LateFeeCorrection(BillRepository billRepository, LateFeeChargeRepository chargeRepository,
                             PaymentAllocationRepository allocationRepository,
                             LateFeeAdjustmentRepository adjustmentRepository, LedgerEntryRepository ledgerRepository,
                             SocietySettingsRepository settingsRepository) {
        this.billRepository = billRepository;
        this.chargeRepository = chargeRepository;
        this.allocationRepository = allocationRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.ledgerRepository = ledgerRepository;
        this.settingsRepository = settingsRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal reverseInterestChargedAfter(LocalDate paidOn, Collection<Long> billIds, Long paymentId,
                                                  LocalDate recordedOn, Instant now, Long actorUserId) {
        BigDecimal annualPct = settingsRepository.current().getLateInterestPctPerAnnum();
        BigDecimal reversed = Money.ZERO;
        for (Long billId : billIds) {
            List<LateFeeCharge> affected = chargeRepository.findByBillIdOrderByToDateAscIdAsc(billId).stream()
                    .filter(charge -> !charge.getToDate().isBefore(paidOn))
                    .toList();
            if (affected.isEmpty()) {
                continue;
            }
            Bill bill = billRepository.findById(billId).orElseThrow();
            List<PrincipalReduction> reductions = allocationRepository
                    .findByBillIdInOrderByAllocatedOnAscIdAsc(List.of(billId)).stream()
                    .map(allocation -> new PrincipalReduction(allocation.getAllocatedOn(), allocation.getAmount()))
                    .toList();
            Map<Long, BigDecimal> adjustedSoFar = adjustedByCharge(affected);

            for (LateFeeCharge charge : affected) {
                BigDecimal correct = LateFeeCalculator.interestOnDecliningPrincipal(bill.getAmount(), reductions,
                        annualPct, charge.getFromDate().minusDays(1), charge.getToDate());
                BigDecimal excess = charge.getAmount()
                        .subtract(adjustedSoFar.getOrDefault(charge.getId(), BigDecimal.ZERO))
                        .subtract(correct);
                if (excess.signum() <= 0) {
                    continue;
                }
                LateFeeAdjustment adjustment = adjustmentRepository.save(
                        new LateFeeAdjustment(charge.getId(), paymentId, excess, now));
                ledgerRepository.save(LedgerEntry.creditForLateFeeAdjustment(bill.getFlatId(), adjustment.getId(),
                        recordedOn, excess, "Late interest adjusted for payment of " + DAY.format(paidOn),
                        now, actorUserId));
                reversed = reversed.add(excess);
            }
        }
        return reversed;
    }

    private Map<Long, BigDecimal> adjustedByCharge(List<LateFeeCharge> charges) {
        return adjustmentRepository.findByLateFeeChargeIdIn(charges.stream().map(LateFeeCharge::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(LateFeeAdjustment::getLateFeeChargeId,
                        Collectors.reducing(BigDecimal.ZERO, LateFeeAdjustment::getAmount, BigDecimal::add)));
    }
}
