package com.isocode.township.matching;

import com.isocode.township.auth.AppUser;
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.common.NotFoundException;
import com.isocode.township.flat.FlatRepository;
import com.isocode.township.matching.dto.BankImportDetails;
import com.isocode.township.matching.dto.BankImportSummary;
import com.isocode.township.matching.dto.BankRowView;
import com.isocode.township.matching.dto.BankRowView.DuplicateRef;
import com.isocode.township.matching.dto.BankRowView.FlatRef;
import com.isocode.township.payment.Payment;
import com.isocode.township.payment.PaymentRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class BankImportViews {

    private final BankImportRepository importRepository;
    private final BankRowRepository rowRepository;
    private final FlatRepository flatRepository;
    private final AppUserRepository userRepository;
    private final PaymentRepository paymentRepository;

    public BankImportViews(BankImportRepository importRepository, BankRowRepository rowRepository,
                           FlatRepository flatRepository, AppUserRepository userRepository,
                           PaymentRepository paymentRepository) {
        this.importRepository = importRepository;
        this.rowRepository = rowRepository;
        this.flatRepository = flatRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
    }

    public List<BankImportSummary> summaries() {
        return importRepository.findAllByOrderByUploadedAtDescIdDesc().stream().map(this::summary).toList();
    }

    public BankImportDetails details(Long importId) {
        BankImport bankImport = importRepository.findById(importId)
                .orElseThrow(() -> new NotFoundException("Bank import not found"));
        return new BankImportDetails(summary(bankImport),
                rowRepository.findByImportIdOrderByRowNoAsc(importId).stream().map(this::row).toList());
    }

    public List<BankRowView> queue() {
        return rowRepository.findOpenQueue().stream().map(this::row).toList();
    }

    public BankRowView row(Long rowId) {
        return row(rowRepository.findById(rowId).orElseThrow(() -> new NotFoundException("Bank row not found")));
    }

    BankImportSummary summary(BankImport bankImport) {
        Instant importedBefore = importRepository.findByFileSha256OrderByUploadedAtAsc(bankImport.getFileSha256())
                .stream()
                .filter(other -> other.getId() < bankImport.getId())
                .map(BankImport::getUploadedAt)
                .findFirst()
                .orElse(null);
        return new BankImportSummary(bankImport.getId(), bankImport.getFileName(), bankImport.getUploadedAt(),
                userName(bankImport.getUploadedBy()), bankImport.getTotalRows(), bankImport.getAutoPosted(),
                bankImport.getReconciled(), bankImport.getSuggested(), bankImport.getConflicts(),
                bankImport.getUnmatched(), bankImport.getDuplicates(), bankImport.getPossibleDuplicates(),
                bankImport.getNonCredit(), bankImport.getErrors(), importedBefore);
    }

    BankRowView row(BankRow row) {
        return new BankRowView(row.getId(), row.getImportId(), row.getRowNo(), row.getTxnDate(), row.getDescription(),
                row.getReference(), row.getAmount(), row.getDebit(), row.getUtr().orElse(null),
                row.getVpa().orElse(null), row.getGatewayRef().orElse(null), row.getDecisionName(), row.getConfidence(),
                flatRef(row.getSuggestedFlatId()), candidates(row), row.getReasons(), duplicateRef(row),
                receiptNo(row.getPaymentId()), row.getResolution(), userName(row.getResolvedBy()), row.getResolvedAt(),
                row.getResolutionNote(), row.getParseError(), row.isOpenInQueue());
    }

    private List<FlatRef> candidates(BankRow row) {
        return row.getCandidates().stream()
                .map(candidate -> new FlatRef(((Number) candidate.get("flatId")).longValue(),
                        (String) candidate.get("flatCode")))
                .toList();
    }

    private FlatRef flatRef(Long flatId) {
        if (flatId == null) {
            return null;
        }
        return flatRepository.findById(flatId).map(flat -> new FlatRef(flat.getId(), flat.getCode())).orElse(null);
    }

    private DuplicateRef duplicateRef(BankRow row) {
        if (row.getDuplicateOfRowId() == null) {
            return null;
        }
        return rowRepository.findById(row.getDuplicateOfRowId())
                .map(earlier -> new DuplicateRef(earlier.getId(), earlier.getImportId(), earlier.getRowNo()))
                .orElse(null);
    }

    private String receiptNo(Long paymentId) {
        return paymentId == null ? null : paymentRepository.findById(paymentId).map(Payment::getReceiptNo).orElse(null);
    }

    private String userName(Long userId) {
        return userId == null ? null : userRepository.findById(userId).map(AppUser::getFullName).orElse(null);
    }
}
