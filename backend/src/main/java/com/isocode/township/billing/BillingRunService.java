package com.isocode.township.billing;

import com.isocode.township.common.BadRequestException;
import com.isocode.township.common.SocietySettings;
import com.isocode.township.common.SocietySettingsRepository;
import com.isocode.township.flat.Flat;
import com.isocode.township.flat.FlatRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingRunService {

    private final SocietySettingsRepository settingsRepository;
    private final FlatRepository flatRepository;
    private final BillRepository billRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final LateFeePostingService lateFeePostingService;
    private final Clock clock;

    public BillingRunService(SocietySettingsRepository settingsRepository, FlatRepository flatRepository,
                             BillRepository billRepository, LedgerEntryRepository ledgerRepository,
                             LateFeePostingService lateFeePostingService, Clock clock) {
        this.settingsRepository = settingsRepository;
        this.flatRepository = flatRepository;
        this.billRepository = billRepository;
        this.ledgerRepository = ledgerRepository;
        this.lateFeePostingService = lateFeePostingService;
        this.clock = clock;
    }

    @Transactional
    public BillingRunResult run(YearMonth month, Long actorUserId) {
        if (month.isAfter(YearMonth.now(clock))) {
            throw new BadRequestException("Bills can only be generated up to the current month.");
        }
        SocietySettings settings = settingsRepository.lockForBillingRun();
        Instant now = clock.instant();

        int billsCreated = createMissingBills(month, settings, now, actorUserId);
        LocalDate interestUpTo = month.atDay(1).minusDays(1);
        LateFeePostingResult lateFees = lateFeePostingService.postInterestUpTo(
                interestUpTo, settings.getLateInterestPctPerAnnum(), now, actorUserId);

        return new BillingRunResult(month, billsCreated, interestUpTo, lateFees.chargesPosted(), lateFees.total());
    }

    private int createMissingBills(YearMonth month, SocietySettings settings, Instant now, Long actorUserId) {
        Set<Long> alreadyBilled = billRepository.findFlatIdsBilledFor(month.atDay(1));
        BigDecimal rate = settings.getRatePerSqft();
        LocalDate dueDate = month.atDay(settings.getDueDay());
        int created = 0;
        for (Flat flat : flatRepository.findAll(Sort.by("id"))) {
            if (alreadyBilled.contains(flat.getId())) {
                continue;
            }
            BigDecimal amount = BillCalculator.amount(flat.getAreaSqft(), rate);
            Bill bill = billRepository.save(new Bill(flat.getId(), month, flat.getAreaSqft(), rate, amount, dueDate, now));
            ledgerRepository.save(LedgerEntry.debitForBill(bill, LedgerDescriptions.bill(month), now, actorUserId));
            created++;
        }
        return created;
    }
}
