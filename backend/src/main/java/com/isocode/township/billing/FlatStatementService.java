package com.isocode.township.billing;

import com.isocode.township.billing.dto.BillResponse;
import com.isocode.township.billing.dto.FlatSummaryResponse;
import com.isocode.township.billing.dto.LedgerLineResponse;
import com.isocode.township.common.Money;
import com.isocode.township.common.NotFoundException;
import com.isocode.township.common.SocietySettingsRepository;
import com.isocode.township.flat.Flat;
import com.isocode.township.flat.FlatRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FlatStatementService {

    private final FlatRepository flatRepository;
    private final BillRepository billRepository;
    private final LateFeeChargeRepository chargeRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final SocietySettingsRepository settingsRepository;
    private final Clock clock;

    public FlatStatementService(FlatRepository flatRepository, BillRepository billRepository,
                                LateFeeChargeRepository chargeRepository, LedgerEntryRepository ledgerRepository,
                                SocietySettingsRepository settingsRepository, Clock clock) {
        this.flatRepository = flatRepository;
        this.billRepository = billRepository;
        this.chargeRepository = chargeRepository;
        this.ledgerRepository = ledgerRepository;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    public FlatSummaryResponse summary(Long flatId) {
        Flat flat = requireFlat(flatId);
        LocalDate today = LocalDate.now(clock);
        BigDecimal balance = Money.rounded(ledgerRepository.balanceOf(flatId));
        List<Bill> unpaidBills = billRepository.findUnpaidByFlat(flatId);

        LocalDate oldestUnpaidDueDate = unpaidBills.isEmpty() ? null : unpaidBills.getFirst().getDueDate();
        long daysOverdue = oldestUnpaidDueDate != null && today.isAfter(oldestUnpaidDueDate)
                ? ChronoUnit.DAYS.between(oldestUnpaidDueDate, today)
                : 0;

        return new FlatSummaryResponse(
                flat.getId(),
                flat.getCode(),
                today,
                balance.max(Money.ZERO),
                balance.negate().max(Money.ZERO),
                accruedLateFee(unpaidBills, today),
                oldestUnpaidDueDate,
                daysOverdue);
    }

    public List<BillResponse> bills(Long flatId) {
        requireFlat(flatId);
        LocalDate today = LocalDate.now(clock);
        return billRepository.findByFlatIdOrderByPeriodDesc(flatId).stream()
                .map(bill -> new BillResponse(bill.getId(), bill.getMonth(), bill.getAreaSqft(), bill.getRate(),
                        bill.getAmount(), bill.getAmountPaid(), bill.principalOutstanding(), bill.getDueDate(),
                        BillStatus.of(bill, today)))
                .toList();
    }

    public List<LedgerLineResponse> ledger(Long flatId) {
        requireFlat(flatId);
        BigDecimal runningBalance = Money.ZERO;
        List<LedgerLineResponse> lines = new ArrayList<>();
        for (LedgerEntry entry : ledgerRepository.findByFlatIdOrderByEntryDateAscIdAsc(flatId)) {
            runningBalance = runningBalance.add(entry.signedAmount());
            lines.add(new LedgerLineResponse(entry.getId(), entry.getEntryDate(), entry.getType(),
                    entry.getDescription(), entry.getDebit(), entry.getCredit(), runningBalance));
        }
        return lines;
    }

    private BigDecimal accruedLateFee(List<Bill> unpaidBills, LocalDate today) {
        if (unpaidBills.isEmpty()) {
            return Money.ZERO;
        }
        BigDecimal annualPct = settingsRepository.current().getLateInterestPctPerAnnum();
        Map<Long, LocalDate> lastChargedDays = chargeRepository.lastChargedDayByBill(
                unpaidBills.stream().map(Bill::getId).toList());
        BigDecimal accrued = Money.ZERO;
        for (Bill bill : unpaidBills) {
            LocalDate startsAfter = LateFeeCalculator.interestStartsAfter(bill.getDueDate(), lastChargedDays.get(bill.getId()));
            accrued = accrued.add(LateFeeCalculator.interest(bill.principalOutstanding(), annualPct, startsAfter, today));
        }
        return accrued;
    }

    private Flat requireFlat(Long flatId) {
        return flatRepository.findById(flatId).orElseThrow(() -> new NotFoundException("Flat not found"));
    }
}
