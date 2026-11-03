package com.isocode.township.billing;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LateFeePostingService {

    private final BillRepository billRepository;
    private final LateFeeChargeRepository chargeRepository;
    private final LedgerEntryRepository ledgerRepository;

    public LateFeePostingService(BillRepository billRepository, LateFeeChargeRepository chargeRepository,
                                 LedgerEntryRepository ledgerRepository) {
        this.billRepository = billRepository;
        this.chargeRepository = chargeRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LateFeePostingResult postInterestUpTo(LocalDate lastDayInclusive, BigDecimal annualPct,
                                                 Instant now, Long actorUserId) {
        List<Bill> overdueBills = billRepository.findUnpaidDueBefore(lastDayInclusive);
        Map<Long, LocalDate> lastChargedDays = chargeRepository.lastChargedDayByBill(
                overdueBills.stream().map(Bill::getId).toList());

        int chargesPosted = 0;
        BigDecimal total = Money.ZERO;
        for (Bill bill : overdueBills) {
            LocalDate startsAfter = LateFeeCalculator.interestStartsAfter(
                    bill.getDueDate(), lastChargedDays.get(bill.getId()));
            BigDecimal interest = LateFeeCalculator.interest(
                    bill.principalOutstanding(), annualPct, startsAfter, lastDayInclusive);
            if (interest.signum() <= 0) {
                continue;
            }
            LocalDate firstDay = startsAfter.plusDays(1);
            LateFeeCharge charge = chargeRepository.save(new LateFeeCharge(
                    bill.getFlatId(), bill.getId(), firstDay, lastDayInclusive, interest, now));
            ledgerRepository.save(LedgerEntry.debitForLateFee(charge,
                    LedgerDescriptions.lateFee(bill.getMonth(), firstDay, lastDayInclusive), now, actorUserId));
            chargesPosted++;
            total = total.add(interest);
        }
        return new LateFeePostingResult(chargesPosted, total);
    }
}
