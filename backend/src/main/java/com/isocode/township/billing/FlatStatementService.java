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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FlatStatementService {

    private final FlatRepository flatRepository;
    private final BillRepository billRepository;
    private final LateInterestService lateInterestService;
    private final OutstandingLateFees outstandingLateFees;
    private final LedgerEntryRepository ledgerRepository;
    private final SocietySettingsRepository settingsRepository;
    private final Clock clock;

    public FlatStatementService(FlatRepository flatRepository, BillRepository billRepository,
                                LateInterestService lateInterestService, OutstandingLateFees outstandingLateFees,
                                LedgerEntryRepository ledgerRepository, SocietySettingsRepository settingsRepository,
                                Clock clock) {
        this.flatRepository = flatRepository;
        this.billRepository = billRepository;
        this.lateInterestService = lateInterestService;
        this.outstandingLateFees = outstandingLateFees;
        this.ledgerRepository = ledgerRepository;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    public FlatSummaryResponse summary(Long flatId) {
        Flat flat = requireFlat(flatId);
        LocalDate today = LocalDate.now(clock);
        BigDecimal balance = Money.rounded(ledgerRepository.balanceOf(flatId));
        List<Bill> unpaidBills = billRepository.findUnpaidByFlat(flatId);

        LocalDate oldestUnpaidDueDate = unpaidBills.stream().map(Bill::getDueDate).min(LocalDate::compareTo).orElse(null);
        long daysOverdue = oldestUnpaidDueDate != null && today.isAfter(oldestUnpaidDueDate)
                ? ChronoUnit.DAYS.between(oldestUnpaidDueDate, today)
                : 0;

        BigDecimal overduePrincipal = unpaidBills.stream()
                .filter(bill -> today.isAfter(bill.getDueDate()))
                .map(Bill::principalOutstanding)
                .reduce(Money.ZERO, BigDecimal::add);
        BigDecimal unpaidLateFees = outstandingLateFees.forFlat(flatId).stream()
                .map(OutstandingLateFees.Balance::outstanding)
                .reduce(Money.ZERO, BigDecimal::add);

        return new FlatSummaryResponse(
                flat.getId(),
                flat.getCode(),
                today,
                balance.max(Money.ZERO),
                overduePrincipal.add(unpaidLateFees),
                balance.negate().max(Money.ZERO),
                accruedLateFee(flatId, today),
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
        Map<Long, String> receiptByPayment = ledgerRepository.findReceiptsForFlat(flatId).stream()
                .collect(Collectors.toMap(LedgerEntryRepository.PaymentReceipt::getPaymentId,
                        LedgerEntryRepository.PaymentReceipt::getReceiptNo));
        BigDecimal runningBalance = Money.ZERO;
        List<LedgerLineResponse> lines = new ArrayList<>();
        for (LedgerEntry entry : ledgerRepository.findByFlatIdOrderByEntryDateAscIdAsc(flatId)) {
            runningBalance = runningBalance.add(entry.signedAmount());
            String receiptNo = LedgerEntry.REF_PAYMENT.equals(entry.getRefType())
                    ? receiptByPayment.get(entry.getRefId())
                    : null;
            lines.add(new LedgerLineResponse(entry.getId(), entry.getEntryDate(), entry.getType(),
                    entry.getDescription(), entry.getDebit(), entry.getCredit(), runningBalance, receiptNo));
        }
        return lines;
    }

    private BigDecimal accruedLateFee(Long flatId, LocalDate today) {
        List<Bill> candidates = billRepository.findInterestCandidatesForFlat(flatId, today);
        if (candidates.isEmpty()) {
            return Money.ZERO;
        }
        BigDecimal annualPct = settingsRepository.current().getLateInterestPctPerAnnum();
        return lateInterestService.unchargedInterest(candidates, today, annualPct).stream()
                .map(BillInterest::amount)
                .reduce(Money.ZERO, BigDecimal::add);
    }

    private Flat requireFlat(Long flatId) {
        return flatRepository.findById(flatId).orElseThrow(() -> new NotFoundException("Flat not found"));
    }
}
