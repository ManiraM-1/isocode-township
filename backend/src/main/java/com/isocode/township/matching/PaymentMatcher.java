package com.isocode.township.matching;

import com.isocode.township.common.IndianRupees;
import com.isocode.township.matching.FlatReferences.ReferenceSignal;
import com.isocode.township.matching.MatchContext.AmountKind;
import com.isocode.township.matching.MatchContext.AmountMatch;
import com.isocode.township.matching.MatchContext.GatewayPayment;
import com.isocode.township.matching.MatchResult.Candidate;
import com.isocode.township.matching.MatchResult.DuplicateOf;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class PaymentMatcher {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private PaymentMatcher() {
    }

    public static List<MatchResult> matchAll(List<StatementRow> rows, MatchContext context) {
        List<MatchResult> results = new ArrayList<>(rows.size());
        for (StatementRow row : rows.stream().sorted(Comparator.comparingInt(StatementRow::rowNo)).toList()) {
            results.add(match(row, context));
            context.rememberRowOfThisImport(row);
        }
        return results;
    }

    public static MatchResult match(StatementRow row, MatchContext context) {
        Optional<MatchResult> duplicate = alreadySeenTransaction(row, context);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        if (row.kind() == RowKind.NON_CREDIT) {
            return result(row, MatchDecision.NON_CREDIT, List.of("Debit or zero amount, not a payment received"));
        }
        if (row.utr().isEmpty()) {
            Optional<DuplicateOf> sameRow = context.seenRowHash(row.rowHash());
            if (sameRow.isPresent()) {
                return possibleDuplicate(row, context, sameRow.get());
            }
        }
        List<String> reasons = new ArrayList<>();
        if (row.gatewayRef().isPresent()) {
            String gatewayRef = row.gatewayRef().get();
            Optional<GatewayPayment> payment = context.gatewayPayment(gatewayRef);
            if (payment.isPresent()) {
                return gatewayDecision(row, context, gatewayRef, payment.get());
            }
            reasons.add("Gateway reference " + gatewayRef + " does not match any payment");
        }
        return decideBySignals(row, context, reasons);
    }

    private static Optional<MatchResult> alreadySeenTransaction(StatementRow row, MatchContext context) {
        for (Optional<String> id : List.of(row.utr(), row.gatewayRef())) {
            if (id.isEmpty()) {
                continue;
            }
            Optional<MatchContext.SeenTransaction> seen = context.seenTransaction(id.get());
            if (seen.isPresent()) {
                String label = id.equals(row.gatewayRef()) ? "Gateway reference " : "UTR ";
                return Optional.of(new MatchResult(row.rowNo(), MatchDecision.DUPLICATE, Optional.empty(), List.of(),
                        Optional.empty(), List.of(label + id.get() + " " + seen.get().where()),
                        Optional.ofNullable(seen.get().duplicateOf()), Optional.empty()));
            }
        }
        return Optional.empty();
    }

    private static MatchResult possibleDuplicate(StatementRow row, MatchContext context, DuplicateOf earlier) {
        MatchResult bySignals = decideBySignals(row, context, new ArrayList<>());
        List<String> reasons = new ArrayList<>();
        reasons.add("Same date, description, reference and amount as " + earlier.describe()
                + ", and no UTR to tell them apart");
        reasons.addAll(bySignals.reasons());
        return new MatchResult(row.rowNo(), MatchDecision.POSSIBLE_DUPLICATE, bySignals.flatId(),
                bySignals.candidates(), Optional.of(Confidence.LOW), List.copyOf(reasons), Optional.of(earlier),
                Optional.empty());
    }

    private static MatchResult gatewayDecision(StatementRow row, MatchContext context, String gatewayRef,
                                               GatewayPayment payment) {
        String flatCode = context.codeOf(payment.flatId());
        if (payment.amount().compareTo(row.credit()) == 0) {
            return new MatchResult(row.rowNo(), MatchDecision.RECONCILED_GATEWAY, Optional.of(payment.flatId()),
                    List.of(), Optional.of(Confidence.HIGH),
                    List.of("Gateway reference " + gatewayRef + " matches receipt " + payment.receiptNo() + " for "
                            + IndianRupees.format(payment.amount()) + " already posted to " + flatCode),
                    Optional.empty(), Optional.of(payment.paymentId()));
        }
        return new MatchResult(row.rowNo(), MatchDecision.CONFLICT, Optional.empty(),
                List.of(new Candidate(payment.flatId(), flatCode)), Optional.empty(),
                List.of("Gateway reference " + gatewayRef + " is receipt " + payment.receiptNo() + " for "
                        + IndianRupees.format(payment.amount()) + " (" + flatCode + "), but the bank shows "
                        + IndianRupees.format(row.credit())),
                Optional.empty(), Optional.of(payment.paymentId()));
    }

    private static MatchResult decideBySignals(StatementRow row, MatchContext context, List<String> reasons) {
        ReferenceSignal reference = FlatReferences.resolve(row.description(), row.reference(), context.flatCodes());
        reference.unknownCodes().forEach(code -> reasons.add("Reference " + code + " is not a flat in this township"));
        Optional<Long> referenceFlat = reference.namesOneFlat()
                ? Optional.of(context.flatIdOf(reference.knownCodes().getFirst()))
                : Optional.empty();
        referenceFlat.ifPresent(flat -> reasons.add("Reference " + context.codeOf(flat) + " found"));

        Optional<Long> upiFlat = row.upiId().flatMap(context::flatForUpiId);
        row.upiId().ifPresent(upiId -> reasons.add(upiFlat
                .map(flat -> "UPI " + upiId + " is known for " + context.codeOf(flat))
                .orElse("UPI " + upiId + " is not linked to any flat yet")));

        List<AmountMatch> amountMatches = context.amountMatches(row.credit());
        List<Long> amountFlats = amountMatches.stream().map(AmountMatch::flatId).distinct().toList();

        if (reference.isAmbiguous()) {
            reasons.add("Reference names more than one flat: " + String.join(", ", reference.knownCodes()));
            Set<Long> candidates = new LinkedHashSet<>();
            reference.knownCodes().forEach(code -> candidates.add(context.flatIdOf(code)));
            upiFlat.ifPresent(candidates::add);
            return conflict(row, context, reasons, candidates);
        }
        if (referenceFlat.isPresent() && upiFlat.isPresent() && !referenceFlat.equals(upiFlat)) {
            reasons.add("Reference and UPI ID name different flats: " + context.codeOf(referenceFlat.get())
                    + " and " + context.codeOf(upiFlat.get()));
            return conflict(row, context, reasons, new LinkedHashSet<>(List.of(referenceFlat.get(), upiFlat.get())));
        }
        if (referenceFlat.isPresent() && upiFlat.isPresent()) {
            Long flat = referenceFlat.get();
            explainAmountBeside(flat, amountMatches, amountFlats, row, context, reasons, "reference and UPI ID agree");
            return confident(row, MatchDecision.AUTO_POSTED, flat, Confidence.HIGH, reasons);
        }

        Optional<Long> singleSignal = referenceFlat.or(() -> upiFlat);
        if (singleSignal.isPresent()) {
            Long flat = singleSignal.get();
            if (amountFlats.equals(List.of(flat))) {
                reasons.add(amountReason(row, context, amountMatches.getFirst()));
                return confident(row, MatchDecision.AUTO_POSTED, flat, Confidence.HIGH, reasons);
            }
            String basis = referenceFlat.isPresent()
                    ? "the reference names " + context.codeOf(flat)
                    : "the UPI ID is known for " + context.codeOf(flat);
            explainAmountBeside(flat, amountMatches, amountFlats, row, context, reasons, basis);
            return confident(row, MatchDecision.SUGGESTED, flat, Confidence.MEDIUM, reasons);
        }

        if (amountFlats.size() == 1) {
            reasons.add(amountReason(row, context, amountMatches.getFirst()));
            return confident(row, MatchDecision.SUGGESTED, amountFlats.getFirst(), Confidence.MEDIUM, reasons);
        }
        if (amountFlats.size() > 1) {
            reasons.add("Amount " + IndianRupees.format(row.credit()) + " matches more than one flat: "
                    + codes(amountFlats, context));
            return new MatchResult(row.rowNo(), MatchDecision.UNMATCHED, Optional.empty(),
                    candidates(amountFlats, context), Optional.of(Confidence.LOW), List.copyOf(reasons),
                    Optional.empty(), Optional.empty());
        }
        reasons.add("No flat reference, known UPI ID or matching amount");
        return result(row, MatchDecision.UNMATCHED, reasons);
    }

    private static void explainAmountBeside(Long flat, List<AmountMatch> amountMatches, List<Long> amountFlats,
                                            StatementRow row, MatchContext context, List<String> reasons,
                                            String basis) {
        if (amountFlats.isEmpty()) {
            return;
        }
        String amount = IndianRupees.format(row.credit());
        if (amountFlats.equals(List.of(flat))) {
            reasons.add(amountReason(row, context, amountMatches.getFirst()));
        } else if (amountFlats.contains(flat)) {
            reasons.add("Amount " + amount + " matches more than one flat (" + codes(amountFlats, context)
                    + "), so it does not confirm " + context.codeOf(flat));
        } else if (amountFlats.size() == 1) {
            reasons.add("Amount " + amount + " also equals " + describeMatch(context, amountMatches.getFirst())
                    + "; ignored because " + basis);
        } else {
            reasons.add("Amount " + amount + " also matches " + codes(amountFlats, context) + "; ignored because "
                    + basis);
        }
    }

    private static String amountReason(StatementRow row, MatchContext context, AmountMatch match) {
        return "Amount " + IndianRupees.format(row.credit()) + " equals " + describeMatch(context, match);
    }

    private static String describeMatch(MatchContext context, AmountMatch match) {
        String code = context.codeOf(match.flatId());
        return match.kind() == AmountKind.BILL_REMAINING
                ? "the remaining amount of " + code + "'s " + MONTH.format(match.billMonth()) + " bill"
                : code + "'s total outstanding";
    }

    private static MatchResult conflict(StatementRow row, MatchContext context, List<String> reasons,
                                        Set<Long> candidateFlats) {
        return new MatchResult(row.rowNo(), MatchDecision.CONFLICT, Optional.empty(),
                candidates(List.copyOf(candidateFlats), context), Optional.empty(), List.copyOf(reasons),
                Optional.empty(), Optional.empty());
    }

    private static MatchResult confident(StatementRow row, MatchDecision decision, Long flat, Confidence confidence,
                                         List<String> reasons) {
        return new MatchResult(row.rowNo(), decision, Optional.of(flat), List.of(), Optional.of(confidence),
                List.copyOf(reasons), Optional.empty(), Optional.empty());
    }

    private static MatchResult result(StatementRow row, MatchDecision decision, List<String> reasons) {
        return new MatchResult(row.rowNo(), decision, Optional.empty(), List.of(), Optional.empty(),
                List.copyOf(reasons), Optional.empty(), Optional.empty());
    }

    private static List<Candidate> candidates(List<Long> flats, MatchContext context) {
        return flats.stream().map(flat -> new Candidate(flat, context.codeOf(flat))).toList();
    }

    private static String codes(List<Long> flats, MatchContext context) {
        return flats.stream().map(context::codeOf).sorted().collect(Collectors.joining(", "));
    }
}
