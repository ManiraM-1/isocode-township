package com.isocode.township.billing;

import com.isocode.township.common.BadRequestException;
import com.isocode.township.common.NotFoundException;
import com.isocode.township.common.SocietySettings;
import com.isocode.township.common.SocietySettingsRepository;
import com.isocode.township.flat.Flat;
import com.isocode.township.flat.FlatRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BillingRunService(SocietySettingsRepository settingsRepository, FlatRepository flatRepository,
                             BillRepository billRepository, LedgerEntryRepository ledgerRepository,
                             LateFeePostingService lateFeePostingService, ApplicationEventPublisher events,
                             Clock clock) {
        this.settingsRepository = settingsRepository;
        this.flatRepository = flatRepository;
        this.billRepository = billRepository;
        this.ledgerRepository = ledgerRepository;
        this.lateFeePostingService = lateFeePostingService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public BillingRunResult run(YearMonth month, Long actorUserId) {
        rejectFutureMonth(month);
        SocietySettings settings = settingsRepository.lockForBillingRun();
        Instant now = clock.instant();

        Set<Long> billedFlats = createMissingBills(month, settings, now, actorUserId);
        events.publishEvent(new BillsIssuedEvent(month.atDay(1), billedFlats));

        LocalDate interestUpTo = month.atDay(1).minusDays(1);
        LateFeePostingResult lateFees = lateFeePostingService.postInterestUpTo(
                interestUpTo, settings.getLateInterestPctPerAnnum(), now, actorUserId);

        return new BillingRunResult(month, billedFlats.size(), interestUpTo, lateFees.chargesPosted(), lateFees.total());
    }

    @Transactional
    public Bill issueBill(Long flatId, YearMonth month, LocalDate dueDate, Long actorUserId) {
        rejectFutureMonth(month);
        SocietySettings settings = settingsRepository.lockForBillingRun();
        Flat flat = flatRepository.findById(flatId).orElseThrow(() -> new NotFoundException("Flat not found"));
        Bill bill = saveBillWithLedgerDebit(flat, month, dueDate, settings.getRatePerSqft(), clock.instant(), actorUserId);
        events.publishEvent(new BillsIssuedEvent(month.atDay(1), Set.of(flatId)));
        return bill;
    }

    private Set<Long> createMissingBills(YearMonth month, SocietySettings settings, Instant now, Long actorUserId) {
        Set<Long> alreadyBilled = billRepository.findFlatIdsBilledFor(month.atDay(1));
        LocalDate dueDate = month.atDay(settings.getDueDay());
        Set<Long> billed = new LinkedHashSet<>();
        for (Flat flat : flatRepository.findAll(Sort.by("id"))) {
            if (alreadyBilled.contains(flat.getId())) {
                continue;
            }
            saveBillWithLedgerDebit(flat, month, dueDate, settings.getRatePerSqft(), now, actorUserId);
            billed.add(flat.getId());
        }
        return billed;
    }

    private Bill saveBillWithLedgerDebit(Flat flat, YearMonth month, LocalDate dueDate, BigDecimal rate,
                                         Instant now, Long actorUserId) {
        BigDecimal amount = BillCalculator.amount(flat.getAreaSqft(), rate);
        Bill bill = billRepository.save(new Bill(flat.getId(), month, flat.getAreaSqft(), rate, amount, dueDate, now));
        ledgerRepository.save(LedgerEntry.debitForBill(bill, LedgerDescriptions.bill(month), now, actorUserId));
        return bill;
    }

    private void rejectFutureMonth(YearMonth month) {
        if (month.isAfter(YearMonth.now(clock))) {
            throw new BadRequestException("Bills can only be generated up to the current month.");
        }
    }
}
