package com.isocode.township.billing;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LateFeePostingService {

    private final BillRepository billRepository;
    private final LateFeeChargeRepository chargeRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final LateInterestService lateInterestService;

    public LateFeePostingService(BillRepository billRepository, LateFeeChargeRepository chargeRepository,
                                 LedgerEntryRepository ledgerRepository, LateInterestService lateInterestService) {
        this.billRepository = billRepository;
        this.chargeRepository = chargeRepository;
        this.ledgerRepository = ledgerRepository;
        this.lateInterestService = lateInterestService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LateFeePostingResult postInterestUpTo(LocalDate lastDayInclusive, BigDecimal annualPct,
                                                 Instant now, Long actorUserId) {
        int chargesPosted = 0;
        BigDecimal total = Money.ZERO;
        for (BillInterest interest : lateInterestService.unchargedInterest(
                billRepository.findInterestCandidates(lastDayInclusive), lastDayInclusive, annualPct)) {
            if (interest.amount().signum() <= 0) {
                continue;
            }
            Bill bill = interest.bill();
            LocalDate firstDay = interest.startsAfter().plusDays(1);
            LateFeeCharge charge = chargeRepository.save(new LateFeeCharge(
                    bill.getFlatId(), bill.getId(), firstDay, lastDayInclusive, interest.amount(), now));
            ledgerRepository.save(LedgerEntry.debitForLateFee(charge,
                    LedgerDescriptions.lateFee(bill.getMonth(), firstDay, lastDayInclusive), now, actorUserId));
            chargesPosted++;
            total = total.add(interest.amount());
        }
        return new LateFeePostingResult(chargesPosted, total);
    }
}
