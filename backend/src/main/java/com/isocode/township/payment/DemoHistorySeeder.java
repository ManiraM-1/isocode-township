package com.isocode.township.payment;

import com.isocode.township.auth.AppUser;
import com.isocode.township.billing.Bill;
import com.isocode.township.billing.BillRepository;
import com.isocode.township.billing.BillingRunService;
import com.isocode.township.common.Money;
import com.isocode.township.common.SocietySettingsRepository;
import com.isocode.township.flat.Flat;
import com.isocode.township.flat.FlatMember;
import com.isocode.township.flat.FlatMemberRepository;
import com.isocode.township.flat.FlatRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoHistorySeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoHistorySeeder.class);

    public static final String NEERAJ_FLAT = "T07-1203";
    public static final String SWATI_FLAT = "T12-0408";
    public static final String AAMIR_FLAT = "T21-1601";
    public static final int SWATI_DAYS_OVERDUE = 47;
    public static final int AAMIR_DAYS_OVERDUE = 18;
    public static final BigDecimal AAMIR_PART_PAYMENT = Money.of("5000.00");

    private static final int MONTHS_OF_HISTORY = 3;
    private static final int DAYS_BEFORE_DUE_FOR_PROMPT_PAYERS = 2;
    private static final long RANDOM_SEED = 42L;
    private static final double ON_TIME_SHARE = 0.70;
    private static final double LATE_SHARE = 0.15;
    private static final double PARTIAL_SHARE = 0.10;
    private static final int MAX_DAYS_LATE = 20;
    private static final BigDecimal PARTIAL_ROUNDING = new BigDecimal("100");
    private static final double ONLINE_SHARE = 0.35;
    private static final double UPI_SHARE = 0.45;
    private static final double BANK_TRANSFER_SHARE = 0.15;
    private static final LocalTime PAYMENT_TIME = LocalTime.of(10, 0);

    private final BillingRunService billingRunService;
    private final PaymentPostingService postingService;
    private final PaymentIntentRepository intentRepository;
    private final FlatRepository flatRepository;
    private final FlatMemberRepository memberRepository;
    private final BillRepository billRepository;
    private final SocietySettingsRepository settingsRepository;
    private final Clock clock;

    public DemoHistorySeeder(BillingRunService billingRunService, PaymentPostingService postingService,
                             PaymentIntentRepository intentRepository, FlatRepository flatRepository,
                             FlatMemberRepository memberRepository, BillRepository billRepository,
                             SocietySettingsRepository settingsRepository, Clock clock) {
        this.billingRunService = billingRunService;
        this.postingService = postingService;
        this.intentRepository = intentRepository;
        this.flatRepository = flatRepository;
        this.memberRepository = memberRepository;
        this.billRepository = billRepository;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    @Transactional
    public void seed() {
        LocalDate today = LocalDate.now(clock);
        YearMonth firstMonth = YearMonth.from(today).minusMonths(MONTHS_OF_HISTORY - 1);
        Long neeraj = flatId(NEERAJ_FLAT);
        Long swati = flatId(SWATI_FLAT);
        Long aamir = flatId(AAMIR_FLAT);
        LocalDate swatiOldestDue = today.minusDays(SWATI_DAYS_OVERDUE);
        LocalDate aamirPartPaidDue = today.minusDays(AAMIR_DAYS_OVERDUE);
        YearMonth aamirPartPaidMonth = YearMonth.from(aamirPartPaidDue);

        Random random = new Random(RANDOM_SEED);
        References references = new References(random);
        List<PlannedPayment> pending = new ArrayList<>();
        Map<Long, PayerHabit> habits = assignHabitsByQuota(Set.of(neeraj, swati, aamir), random);

        for (int offset = 0; offset < MONTHS_OF_HISTORY; offset++) {
            YearMonth month = firstMonth.plusMonths(offset);
            postPaymentsDatedBefore(month.atDay(1), pending, references);

            LocalDate standardDue = month.atDay(settingsRepository.current().getDueDay());
            billingRunService.issueBill(swati, month, laterOf(standardDue, swatiOldestDue), null);
            if (month.equals(aamirPartPaidMonth)) {
                billingRunService.issueBill(aamir, month, aamirPartPaidDue, null);
            }
            billingRunService.run(month, null);

            for (Bill bill : billsFor(month)) {
                planPayment(bill, today, neeraj, swati, aamir, habits, aamirPartPaidMonth, random, pending);
            }
        }
        postPaymentsDatedBefore(today.plusDays(1), pending, references);
        log.info("Demo payment history seeded for {} months", MONTHS_OF_HISTORY);
    }

    private void planPayment(Bill bill, LocalDate today, Long neeraj, Long swati, Long aamir,
                             Map<Long, PayerHabit> habits, YearMonth aamirPartPaidMonth, Random random,
                             List<PlannedPayment> pending) {
        LocalDate issued = bill.getMonth().atDay(1);
        Long flatId = bill.getFlatId();
        if (flatId.equals(swati)) {
            return;
        }
        if (flatId.equals(neeraj)) {
            pending.add(new PlannedPayment(flatId, bill.getAmount(), promptPaymentDay(bill, today), PaymentMethod.UPI));
            return;
        }
        if (flatId.equals(aamir)) {
            if (bill.getMonth().isBefore(aamirPartPaidMonth)) {
                pending.add(new PlannedPayment(flatId, bill.getAmount(), promptPaymentDay(bill, today), PaymentMethod.UPI));
            } else if (bill.getMonth().equals(aamirPartPaidMonth)) {
                pending.add(new PlannedPayment(flatId, AAMIR_PART_PAYMENT, promptPaymentDay(bill, today), PaymentMethod.UPI));
            }
            return;
        }

        PayerHabit habit = habits.get(flatId);
        int dayOffset = random.nextInt(bill.getDueDate().getDayOfMonth());
        int daysLate = 1 + random.nextInt(MAX_DAYS_LATE);
        double partialShare = 0.4 + random.nextDouble() * 0.4;
        PaymentMethod method = randomMethod(random);

        switch (habit) {
            case ON_TIME -> pending.add(new PlannedPayment(flatId, bill.getAmount(), issued.plusDays(dayOffset), method));
            case LATE -> pending.add(new PlannedPayment(flatId, bill.getAmount(),
                    bill.getDueDate().plusDays(daysLate), method));
            case PARTIAL -> {
                BigDecimal partial = bill.getAmount().multiply(BigDecimal.valueOf(partialShare))
                        .divide(PARTIAL_ROUNDING, 0, RoundingMode.DOWN).multiply(PARTIAL_ROUNDING);
                pending.add(new PlannedPayment(flatId, Money.rounded(partial), issued.plusDays(dayOffset), method));
            }
            case NON_PAYER -> {
            }
        }
    }

    private Map<Long, PayerHabit> assignHabitsByQuota(Set<Long> briefFlats, Random random) {
        List<Long> otherFlats = new ArrayList<>(flatRepository.findAll(Sort.by("id")).stream()
                .map(Flat::getId)
                .filter(id -> !briefFlats.contains(id))
                .toList());
        Collections.shuffle(otherFlats, random);
        int onTime = Math.round(otherFlats.size() * (float) ON_TIME_SHARE);
        int late = Math.round(otherFlats.size() * (float) LATE_SHARE);
        int partial = Math.round(otherFlats.size() * (float) PARTIAL_SHARE);

        Map<Long, PayerHabit> habits = new HashMap<>();
        for (int index = 0; index < otherFlats.size(); index++) {
            PayerHabit habit = index < onTime ? PayerHabit.ON_TIME
                    : index < onTime + late ? PayerHabit.LATE
                    : index < onTime + late + partial ? PayerHabit.PARTIAL
                    : PayerHabit.NON_PAYER;
            habits.put(otherFlats.get(index), habit);
        }
        return habits;
    }

    private enum PayerHabit {
        ON_TIME,
        LATE,
        PARTIAL,
        NON_PAYER
    }

    private void postPaymentsDatedBefore(LocalDate cutoffExclusive, List<PlannedPayment> pending,
                                         References references) {
        List<PlannedPayment> due = pending.stream()
                .filter(payment -> payment.paidOn().isBefore(cutoffExclusive))
                .sorted(Comparator.comparing(PlannedPayment::paidOn).thenComparing(PlannedPayment::flatId))
                .toList();
        pending.removeAll(due);
        for (PlannedPayment payment : due) {
            post(payment, references);
        }
    }

    private void post(PlannedPayment planned, References references) {
        Optional<AppUser> payer = signInMember(planned.flatId());
        PaymentMethod method = planned.method() == PaymentMethod.ONLINE_TEST && payer.isEmpty()
                ? PaymentMethod.UPI
                : planned.method();
        String gatewayRef = null;
        String utr = null;
        PaymentSource source;
        switch (method) {
            case ONLINE_TEST -> {
                source = PaymentSource.GATEWAY;
                gatewayRef = references.nextGatewayRef();
                PaymentIntent intent = new PaymentIntent(planned.flatId(), planned.amount(),
                        "seed-" + gatewayRef, gatewayRef, payer.orElseThrow().getId(), at(planned.paidOn()));
                intent.markSucceeded(at(planned.paidOn()));
                intentRepository.save(intent);
            }
            case UPI, BANK_TRANSFER -> {
                source = PaymentSource.BANK_STATEMENT;
                utr = references.nextUtr();
            }
            default -> source = PaymentSource.MANUAL;
        }
        postingService.post(new PostPaymentCommand(planned.flatId(), planned.amount(), method, source,
                gatewayRef, utr, planned.paidOn(), null));
    }

    private LocalDate promptPaymentDay(Bill bill, LocalDate today) {
        LocalDate preferred = bill.getDueDate().minusDays(DAYS_BEFORE_DUE_FOR_PROMPT_PAYERS);
        LocalDate issued = bill.getMonth().atDay(1);
        LocalDate notBeforeIssue = preferred.isBefore(issued) ? issued : preferred;
        return notBeforeIssue.isAfter(today) ? today : notBeforeIssue;
    }

    private List<Bill> billsFor(YearMonth month) {
        return billRepository.findByPeriodOrderByFlatIdAsc(month.atDay(1));
    }

    private Optional<AppUser> signInMember(Long flatId) {
        return memberRepository.findByFlatIdAndActiveTrue(flatId).stream()
                .map(FlatMember::getUser)
                .filter(user -> user.getPhone().isPresent())
                .findFirst();
    }

    private Instant at(LocalDate day) {
        return day.atTime(PAYMENT_TIME).atZone(clock.getZone()).toInstant();
    }

    private Long flatId(String code) {
        return flatRepository.findByCode(code).orElseThrow().getId();
    }

    private static LocalDate laterOf(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static PaymentMethod randomMethod(Random random) {
        double draw = random.nextDouble();
        if (draw < ONLINE_SHARE) {
            return PaymentMethod.ONLINE_TEST;
        }
        if (draw < ONLINE_SHARE + UPI_SHARE) {
            return PaymentMethod.UPI;
        }
        if (draw < ONLINE_SHARE + UPI_SHARE + BANK_TRANSFER_SHARE) {
            return PaymentMethod.BANK_TRANSFER;
        }
        return PaymentMethod.CHEQUE;
    }

    private record PlannedPayment(Long flatId, BigDecimal amount, LocalDate paidOn, PaymentMethod method) {
    }

    private static final class References {

        private static final String GATEWAY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        private static final long UTR_SPACE = 100_000_000_000L;

        private final Random random;
        private final Set<String> used = new HashSet<>();

        References(Random random) {
            this.random = random;
        }

        String nextUtr() {
            String utr;
            do {
                utr = "6%011d".formatted(Math.floorMod(random.nextLong(), UTR_SPACE));
            } while (!used.add(utr));
            return utr;
        }

        String nextGatewayRef() {
            String reference;
            do {
                StringBuilder builder = new StringBuilder("PGTEST-");
                for (int i = 0; i < 12; i++) {
                    builder.append(GATEWAY_ALPHABET.charAt(random.nextInt(GATEWAY_ALPHABET.length())));
                }
                reference = builder.toString();
            } while (!used.add(reference));
            return reference;
        }
    }
}
