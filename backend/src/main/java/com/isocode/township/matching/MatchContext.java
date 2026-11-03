package com.isocode.township.matching;

import com.isocode.township.common.Money;
import com.isocode.township.matching.MatchResult.DuplicateOf;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MatchContext {

    private final Map<String, Long> flatIdByCode;
    private final Map<Long, String> codeByFlatId;
    private final Map<String, Long> flatIdByUpiId;
    private final Map<BigDecimal, List<AmountMatch>> amountMatches;
    private final Map<String, GatewayPayment> gatewayPayments;
    private final Map<String, SeenTransaction> seenTransactions;
    private final Map<String, DuplicateOf> seenRowHashes;
    private final Map<Long, BigDecimal> outstandingByFlat;
    private final Map<Long, BigDecimal> monthlyBillByFlat;
    private final int autoPostLimitMonths;

    private MatchContext(Builder builder) {
        this.outstandingByFlat = Map.copyOf(builder.outstandingByFlat);
        this.monthlyBillByFlat = Map.copyOf(builder.monthlyBillByFlat);
        this.autoPostLimitMonths = builder.autoPostLimitMonths;
        this.flatIdByCode = Map.copyOf(builder.flatIdByCode);
        this.codeByFlatId = Map.copyOf(builder.codeByFlatId);
        this.flatIdByUpiId = Map.copyOf(builder.flatIdByUpiId);
        this.amountMatches = Map.copyOf(builder.amountMatches);
        this.gatewayPayments = Map.copyOf(builder.gatewayPayments);
        this.seenTransactions = new HashMap<>(builder.seenTransactions);
        this.seenRowHashes = new HashMap<>(builder.seenRowHashes);
    }

    public static Builder builder() {
        return new Builder();
    }

    Set<String> flatCodes() {
        return flatIdByCode.keySet();
    }

    Long flatIdOf(String code) {
        return flatIdByCode.get(code);
    }

    String codeOf(Long flatId) {
        return codeByFlatId.get(flatId);
    }

    Optional<Long> flatForUpiId(String upiId) {
        return Optional.ofNullable(flatIdByUpiId.get(upiId.toLowerCase(Locale.ROOT)));
    }

    List<AmountMatch> amountMatches(BigDecimal amount) {
        return amount == null ? List.of() : amountMatches.getOrDefault(Money.rounded(amount), List.of());
    }

    BigDecimal outstandingOf(Long flatId) {
        return outstandingByFlat.getOrDefault(flatId, Money.ZERO);
    }

    Optional<BigDecimal> autoPostLimit(Long flatId) {
        BigDecimal monthlyBill = monthlyBillByFlat.get(flatId);
        if (monthlyBill == null) {
            return Optional.empty();
        }
        return Optional.of(outstandingOf(flatId).max(Money.ZERO)
                .add(monthlyBill.multiply(BigDecimal.valueOf(autoPostLimitMonths))));
    }

    Optional<GatewayPayment> gatewayPayment(String gatewayRef) {
        return Optional.ofNullable(gatewayPayments.get(gatewayRef));
    }

    Optional<SeenTransaction> seenTransaction(String transactionId) {
        return Optional.ofNullable(seenTransactions.get(transactionId));
    }

    Optional<DuplicateOf> seenRowHash(String rowHash) {
        return Optional.ofNullable(seenRowHashes.get(rowHash));
    }

    void rememberRowOfThisImport(StatementRow row) {
        SeenTransaction inThisFile = new SeenTransaction(
                "appears earlier in this file (row " + row.rowNo() + ")", DuplicateOf.thisImport(row.rowNo()));
        row.utr().ifPresent(utr -> seenTransactions.putIfAbsent(utr, inThisFile));
        row.gatewayRef().ifPresent(ref -> seenTransactions.putIfAbsent(ref, inThisFile));
        seenRowHashes.putIfAbsent(row.rowHash(), DuplicateOf.thisImport(row.rowNo()));
    }

    public enum AmountKind {
        BILL_REMAINING,
        TOTAL_OUTSTANDING
    }

    public record AmountMatch(Long flatId, AmountKind kind, YearMonth billMonth) {
    }

    public record GatewayPayment(Long paymentId, Long flatId, BigDecimal amount, String receiptNo) {
    }

    public record SeenTransaction(String where, DuplicateOf duplicateOf) {
    }

    public static final class Builder {

        private final Map<String, Long> flatIdByCode = new HashMap<>();
        private final Map<Long, String> codeByFlatId = new HashMap<>();
        private final Map<String, Long> flatIdByUpiId = new HashMap<>();
        private final Map<BigDecimal, List<AmountMatch>> amountMatches = new HashMap<>();
        private final Map<String, GatewayPayment> gatewayPayments = new HashMap<>();
        private final Map<String, SeenTransaction> seenTransactions = new HashMap<>();
        private final Map<String, DuplicateOf> seenRowHashes = new HashMap<>();
        private final Map<Long, BigDecimal> outstandingByFlat = new HashMap<>();
        private final Map<Long, BigDecimal> monthlyBillByFlat = new HashMap<>();
        private int autoPostLimitMonths = 1;

        private Builder() {
        }

        public Builder monthlyBill(Long flatId, BigDecimal amount) {
            monthlyBillByFlat.put(flatId, Money.rounded(amount));
            return this;
        }

        public Builder autoPostLimitMonths(int months) {
            if (months < 0) {
                throw new IllegalArgumentException("Auto-post limit months cannot be negative");
            }
            this.autoPostLimitMonths = months;
            return this;
        }

        public Builder flat(Long flatId, String code) {
            flatIdByCode.put(code, flatId);
            codeByFlatId.put(flatId, code);
            return this;
        }

        public Builder knownUpiId(String upiId, Long flatId) {
            flatIdByUpiId.put(upiId.toLowerCase(Locale.ROOT), flatId);
            return this;
        }

        public Builder billRemaining(Long flatId, YearMonth month, BigDecimal remaining) {
            return amount(remaining, new AmountMatch(flatId, AmountKind.BILL_REMAINING, month));
        }

        public Builder totalOutstanding(Long flatId, BigDecimal total) {
            outstandingByFlat.put(flatId, Money.rounded(total));
            return amount(total, new AmountMatch(flatId, AmountKind.TOTAL_OUTSTANDING, null));
        }

        public Builder gatewayPayment(String gatewayRef, Long paymentId, Long flatId, BigDecimal amount, String receiptNo) {
            gatewayPayments.put(gatewayRef, new GatewayPayment(paymentId, flatId, Money.rounded(amount), receiptNo));
            return this;
        }

        public Builder postedUtr(String utr, String receiptNo) {
            seenTransactions.put(utr, new SeenTransaction("was already posted as receipt " + receiptNo, null));
            return this;
        }

        public Builder earlierBankRow(Long bankRowId, String rowHash, String utrOrNull, String gatewayRefOrNull) {
            SeenTransaction earlier = new SeenTransaction("is already in an earlier import",
                    DuplicateOf.earlierImport(bankRowId));
            if (utrOrNull != null) {
                seenTransactions.putIfAbsent(utrOrNull, earlier);
            }
            if (gatewayRefOrNull != null) {
                seenTransactions.putIfAbsent(gatewayRefOrNull, earlier);
            }
            seenRowHashes.putIfAbsent(rowHash, DuplicateOf.earlierImport(bankRowId));
            return this;
        }

        public MatchContext build() {
            return new MatchContext(this);
        }

        private Builder amount(BigDecimal amount, AmountMatch match) {
            if (amount != null && amount.signum() > 0) {
                amountMatches.computeIfAbsent(Money.rounded(amount), key -> new ArrayList<>()).add(match);
            }
            return this;
        }
    }
}
