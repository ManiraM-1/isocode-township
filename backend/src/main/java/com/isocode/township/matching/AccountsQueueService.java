package com.isocode.township.matching;

import com.isocode.township.common.AuditService;
import com.isocode.township.common.BadRequestException;
import com.isocode.township.common.ConflictException;
import com.isocode.township.common.NotFoundException;
import com.isocode.township.flat.Flat;
import com.isocode.township.flat.FlatRepository;
import com.isocode.township.payment.Payment;
import com.isocode.township.payment.PaymentPostingService;
import com.isocode.township.payment.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountsQueueService {

    private final BankRowRepository rowRepository;
    private final FlatRepository flatRepository;
    private final KnownUpiIdRepository knownUpiIdRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentPostingService postingService;
    private final AuditService auditService;
    private final Clock clock;

    public AccountsQueueService(BankRowRepository rowRepository, FlatRepository flatRepository,
                                KnownUpiIdRepository knownUpiIdRepository, PaymentRepository paymentRepository,
                                PaymentPostingService postingService, AuditService auditService, Clock clock) {
        this.rowRepository = rowRepository;
        this.flatRepository = flatRepository;
        this.knownUpiIdRepository = knownUpiIdRepository;
        this.paymentRepository = paymentRepository;
        this.postingService = postingService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public Resolved confirm(Long rowId, Long staffUserId) {
        BankRow row = openRow(rowId);
        if (row.getSuggestedFlatId() == null) {
            throw new BadRequestException("This row has no suggested flat. Assign it to a flat instead.");
        }
        return post(row, row.getSuggestedFlatId(), BankRowResolution.CONFIRMED, staffUserId, null);
    }

    @Transactional
    public Resolved assign(Long rowId, Long flatId, String note, Long staffUserId) {
        BankRow row = openRow(rowId);
        flatRepository.findById(flatId).orElseThrow(() -> new NotFoundException("Flat not found"));
        return post(row, flatId, BankRowResolution.ASSIGNED, staffUserId, blankToNull(note));
    }

    @Transactional
    public Resolved ignore(Long rowId, String reason, Long staffUserId) {
        BankRow row = openRow(rowId);
        row.resolve(BankRowResolution.IGNORED, staffUserId, clock.instant(), reason.strip());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("amount", row.getAmount().toPlainString());
        details.put("reason", reason.strip());
        auditService.record(staffUserId, "BANK_ROW_IGNORED", "BANK_ROW", row.getId(), details);
        return new Resolved(row, List.of());
    }

    private Resolved post(BankRow row, Long flatId, BankRowResolution how, Long staffUserId, String note) {
        row.getUtr().flatMap(paymentRepository::findByUtr).ifPresent(existing -> {
            throw new ConflictException("UTR " + existing.getUtr().orElseThrow() + " was already posted as receipt "
                    + existing.getReceiptNo() + ".");
        });
        Instant now = clock.instant();
        Payment payment = postingService.post(BankRowPaymentDetails.commandFor(row, flatId, staffUserId)).payment();
        row.linkPayment(payment.getId());
        row.resolve(how, staffUserId, now, note);
        List<String> warnings = learnUpiId(row, flatId, now);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("flatId", flatId);
        details.put("suggestedFlatId", row.getSuggestedFlatId());
        details.put("amount", row.getAmount().toPlainString());
        details.put("receiptNo", payment.getReceiptNo());
        details.put("note", note);
        auditService.record(staffUserId, "BANK_ROW_" + how.name(), "BANK_ROW", row.getId(), details);
        return new Resolved(row, warnings);
    }

    private List<String> learnUpiId(BankRow row, Long flatId, Instant now) {
        List<String> warnings = new ArrayList<>();
        row.getVpa().ifPresent(upiId -> {
            Optional<KnownUpiId> known = knownUpiIdRepository.findByUpiId(upiId);
            if (known.isEmpty()) {
                knownUpiIdRepository.save(new KnownUpiId(upiId, flatId, row.getId(), now));
            } else if (!known.get().getFlatId().equals(flatId)) {
                String otherFlat = flatRepository.findById(known.get().getFlatId()).map(Flat::getCode).orElse("another flat");
                warnings.add("UPI " + upiId + " is already known for " + otherFlat + "; not changed");
            }
        });
        return warnings;
    }

    private BankRow openRow(Long rowId) {
        BankRow row = rowRepository.findLockedById(rowId).orElseThrow(() -> new NotFoundException("Bank row not found"));
        if (row.getResolution() != null) {
            throw new ConflictException("This row was already resolved.");
        }
        if (!row.isOpenInQueue()) {
            throw new ConflictException("This row is not in the accounts queue.");
        }
        return row;
    }

    private static String blankToNull(String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }

    public record Resolved(BankRow row, List<String> warnings) {
    }
}
