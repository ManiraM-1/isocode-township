package com.isocode.township.matching;

import com.isocode.township.common.AuditService;
import com.isocode.township.common.BadRequestException;
import com.isocode.township.matching.MatchResult.DuplicateOf;
import com.isocode.township.payment.Payment;
import com.isocode.township.payment.PaymentPostingService;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankImportService {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    static final String FUTURE_DATED_REASON = "Dated after today; check the statement before posting";

    private final MatchContextFactory contextFactory;
    private final BankImportRepository importRepository;
    private final BankRowRepository rowRepository;
    private final PaymentPostingService postingService;
    private final AuditService auditService;
    private final Clock clock;

    public BankImportService(MatchContextFactory contextFactory, BankImportRepository importRepository,
                             BankRowRepository rowRepository, PaymentPostingService postingService,
                             AuditService auditService, Clock clock) {
        this.contextFactory = contextFactory;
        this.importRepository = importRepository;
        this.rowRepository = rowRepository;
        this.postingService = postingService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public BankImport importFile(String originalFileName, byte[] content, Long actorUserId) {
        String fileName = validate(originalFileName, content);
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);

        ParsedStatement statement = BankStatementParser.parse(new ByteArrayInputStream(content));
        MatchContext context = contextFactory.build();
        Map<Integer, StatementRow> rowsByNo = statement.rows().stream()
                .collect(Collectors.toMap(StatementRow::rowNo, Function.identity()));
        List<MatchResult> results = PaymentMatcher.matchAll(statement.rows(), context);

        BankImport bankImport = importRepository.save(new BankImport(fileName, sha256(content), actorUserId, now));
        Map<Integer, BankRow> savedByRowNo = new HashMap<>();
        for (MatchResult result : results) {
            BankRow bankRow = BankRow.matched(bankImport.getId(), rowsByNo.get(result.rowNo()), result);
            if (result.decision() == MatchDecision.AUTO_POSTED && bankRow.getTxnDate().isAfter(today)) {
                bankRow.holdForReview(FUTURE_DATED_REASON);
            }
            result.duplicateOf().map(duplicate -> earlierRowId(duplicate, savedByRowNo))
                    .ifPresent(bankRow::linkDuplicateOf);
            savedByRowNo.put(result.rowNo(), rowRepository.save(bankRow));
        }
        statement.errors().forEach(error -> rowRepository.save(BankRow.unreadable(bankImport.getId(), error)));

        for (BankRow bankRow : savedByRowNo.values().stream()
                .sorted(Comparator.comparingInt(BankRow::getRowNo)).toList()) {
            if (MatchDecision.AUTO_POSTED.name().equals(bankRow.getDecisionName())) {
                Payment payment = postingService.post(
                        BankRowPaymentDetails.commandFor(bankRow, bankRow.getSuggestedFlatId(), actorUserId)).payment();
                bankRow.linkPayment(payment.getId());
            }
        }

        Map<String, Integer> counts = savedByRowNo.values().stream()
                .collect(Collectors.groupingBy(BankRow::getDecisionName, Collectors.summingInt(row -> 1)));
        bankImport.recordCounts(counts, statement.errors().size());
        auditService.record(actorUserId, "BANK_STATEMENT_IMPORTED", "BANK_IMPORT", bankImport.getId(),
                auditDetails(bankImport));
        return bankImport;
    }

    private static Long earlierRowId(DuplicateOf duplicate, Map<Integer, BankRow> savedByRowNo) {
        if (duplicate.earlierBankRowId() != null) {
            return duplicate.earlierBankRowId();
        }
        BankRow sameFile = savedByRowNo.get(duplicate.rowNoInThisImport());
        return sameFile == null ? null : sameFile.getId();
    }

    private static String validate(String originalFileName, byte[] content) {
        String fileName = originalFileName == null ? "" : originalFileName.strip();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BadRequestException("Upload the bank statement as a .csv file.");
        }
        if (content == null || content.length == 0) {
            throw new BadRequestException("The file is empty.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new BadRequestException("The file is larger than 5 MB. Split the statement into smaller files.");
        }
        return fileName.length() <= 200 ? fileName : fileName.substring(fileName.length() - 200);
    }

    private static Map<String, Object> auditDetails(BankImport bankImport) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fileName", bankImport.getFileName());
        details.put("fileSha256", bankImport.getFileSha256());
        details.put("totalRows", bankImport.getTotalRows());
        details.put("autoPosted", bankImport.getAutoPosted());
        details.put("reconciled", bankImport.getReconciled());
        details.put("queued", bankImport.getSuggested() + bankImport.getConflicts() + bankImport.getUnmatched()
                + bankImport.getPossibleDuplicates());
        details.put("duplicates", bankImport.getDuplicates());
        details.put("errors", bankImport.getErrors());
        return details;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is not available", unavailable);
        }
    }
}
