package com.isocode.township.matching;

import com.isocode.township.billing.BillCalculator;
import com.isocode.township.common.IndianRupees;
import com.isocode.township.common.Money;
import com.isocode.township.common.SocietySettings;
import com.isocode.township.common.SocietySettingsRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoStatementGenerator {

    private static final DateTimeFormatter BANK_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal OPENING_BALANCE = Money.of("1000000.00");
    private static final BigDecimal LARGE_AMOUNT_MARGIN = Money.of("50000.00");
    private static final BigDecimal GATEWAY_FEE = Money.of("20.00");
    private static final byte[] BYTE_ORDER_MARK = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final JdbcTemplate jdbc;
    private final SocietySettingsRepository settingsRepository;
    private final Clock clock;

    public DemoStatementGenerator(JdbcTemplate jdbc, SocietySettingsRepository settingsRepository, Clock clock) {
        this.jdbc = jdbc;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public byte[] generate() {
        LocalDate today = LocalDate.now(clock);
        long generation = jdbc.queryForObject("SELECT count(*) FROM bank_import", Long.class);
        References refs = new References(today, generation);
        Facts facts = loadFacts();
        List<Line> lines = new ArrayList<>();

        lines.add(Line.debit(today.minusDays(1), "NEFT DR-MSEDCL BILL PAYMENT", refs.reference("N", "electricity", 15),
                Money.of("98450.00")));

        facts.upiFlats().stream().findFirst().ifPresent(flat -> lines.add(Line.credit(today,
                "UPI/CR/" + refs.utr("agree", flat.code()) + "/RESIDENT/" + flat.upiId() + "/" + flat.code() + " maintenance",
                refs.utr("agree", flat.code()), flat.oldestBillRemaining())));

        facts.uniqueBills().stream().findFirst().ifPresent(bill -> lines.add(Line.credit(today,
                "NEFT CR-HDFC0001234-" + bill.code().replace('-', '/') + " MAINTENANCE",
                refs.reference("N", "reference-and-amount" + bill.code(), 15), bill.remaining())));

        String referenceOnlyFlat = facts.flatCodes().get(facts.flatCodes().size() / 2);
        lines.add(Line.credit(today, "IMPS/P2A/" + refs.utr("reference", referenceOnlyFlat) + "/TOWER "
                        + Integer.parseInt(referenceOnlyFlat.substring(1, 3)) + " FLAT " + referenceOnlyFlat.substring(4)
                        + "/MAINTENANCE",
                refs.utr("reference", referenceOnlyFlat), facts.unusedAmount("1500.00")));

        if (facts.upiFlats().size() > 1) {
            UpiFlat flat = facts.upiFlats().get(1);
            lines.add(Line.credit(today, "UPI/CR/" + refs.utr("upi", flat.code()) + "/RESIDENT/" + flat.upiId() + "/NOV",
                    refs.utr("upi", flat.code()), facts.unusedAmount("1200.00")));
        }

        if (facts.uniqueBills().size() > 1) {
            UniqueBill bill = facts.uniqueBills().get(1);
            lines.add(Line.credit(today, "UPI/CR/" + refs.utr("amount", bill.code()) + "/NEW PAYER/new.payer@ybl/MAINTENANCE",
                    refs.utr("amount", bill.code()), bill.remaining()));
        }

        facts.sharedAmount().ifPresent(amount -> lines.add(Line.credit(today,
                "IMPS/P2A/" + refs.utr("shared", amount.toPlainString()) + "/MAINT", refs.utr("shared", amount.toPlainString()),
                amount)));

        if (facts.upiFlats().size() > 2) {
            UpiFlat payer = facts.upiFlats().get(2);
            String otherFlat = facts.flatCodes().stream().filter(code -> !code.equals(payer.code())).findFirst().orElseThrow();
            lines.add(Line.credit(today, "UPI/CR/" + refs.utr("conflict", payer.code()) + "/RESIDENT/" + payer.upiId() + "/"
                    + otherFlat + " on behalf", refs.utr("conflict", payer.code()), facts.unusedAmount("2500.00")));
        }

        lines.add(Line.credit(today, "NEFT CR-ICIC0000456-MAINT " + facts.flatCodes().get(1) + " AND " + facts.flatCodes().get(2),
                refs.reference("N", "ambiguous", 15), facts.unusedAmount("3000.00")));

        if (!facts.unreconciledGateway().isEmpty()) {
            GatewayPayment payment = facts.unreconciledGateway().getFirst();
            lines.add(Line.credit(today, "PG SETTLEMENT " + payment.gatewayRef() + " TESTPAY", payment.gatewayRef(),
                    payment.amount()));
        }
        if (facts.unreconciledGateway().size() > 1) {
            GatewayPayment payment = facts.unreconciledGateway().get(1);
            lines.add(Line.credit(today, "PG SETTLEMENT " + payment.gatewayRef() + " TESTPAY", payment.gatewayRef(),
                    payment.amount().subtract(GATEWAY_FEE)));
        }

        facts.postedUtr().ifPresent(posted -> lines.add(Line.credit(today, "UPI/CR/" + posted.utr() + "/RESIDENT/"
                + posted.code() + " ALREADY RECORDED", posted.utr(), posted.amount())));

        facts.upiFlats().stream().findFirst().ifPresent(flat -> {
            BigDecimal large = flat.autoPostLimit().add(LARGE_AMOUNT_MARGIN)
                    .divide(Money.of("1000.00"), 0, RoundingMode.UP).multiply(Money.of("1000.00"));
            lines.add(Line.credit(today, "UPI/CR/" + refs.utr("large", flat.code()) + "/RESIDENT/" + flat.upiId() + "/"
                    + flat.code() + " corpus and dues", refs.utr("large", flat.code()), Money.rounded(large)));
        });

        String cashFlat = facts.flatCodes().getLast();
        BigDecimal cashAmount = facts.unusedAmount("3500.00");
        String cashSlip = refs.reference("SLIP ", "cash", 6);
        lines.add(Line.credit(today, "CASH DEP " + cashFlat + " MAINT KOREGAON PARK BR " + cashSlip, "", cashAmount));
        lines.add(Line.credit(today, "CASH DEP " + cashFlat + " MAINT KOREGAON PARK BR " + cashSlip, "", cashAmount));

        lines.add(Line.credit(today, "NEFT CR-SBIN0004321-SHREE SAI CATERERS-DEPOSIT REFUND",
                refs.reference("N", "caterer", 15), facts.unusedAmount("123450.00")));
        lines.add(Line.malformed(today, "UPI/CR/" + refs.utr("malformed", "x") + "/MAINT NOV", refs.utr("malformed", "x"),
                "11,537.5O"));

        return toCsv(lines);
    }

    private Facts loadFacts() {
        SocietySettings settings = settingsRepository.current();
        List<String> flatCodes = jdbc.queryForList("SELECT code FROM flat ORDER BY code", String.class);

        Map<String, BigDecimal> outstanding = new HashMap<>();
        jdbc.query("SELECT f.code, COALESCE(sum(e.debit - e.credit), 0) AS balance FROM flat f "
                        + "LEFT JOIN ledger_entry e ON e.flat_id = f.id GROUP BY f.code",
                (RowCallbackHandler) rs -> outstanding.put(rs.getString("code"),
                        rs.getBigDecimal("balance").max(BigDecimal.ZERO)));
        Map<String, BigDecimal> monthlyBill = new HashMap<>();
        jdbc.query("SELECT code, area_sqft FROM flat", (RowCallbackHandler) rs ->
                monthlyBill.put(rs.getString("code"), BillCalculator.amount(rs.getInt("area_sqft"), settings.getRatePerSqft())));

        List<BillRemaining> unpaid = jdbc.query("""
                SELECT f.code, b.amount - b.amount_paid AS remaining FROM bill b JOIN flat f ON f.id = b.flat_id
                WHERE b.amount_paid < b.amount ORDER BY b.period, f.code
                """, (rs, n) -> new BillRemaining(rs.getString("code"), Money.rounded(rs.getBigDecimal("remaining"))));

        Set<BigDecimal> takenAmounts = new HashSet<>();
        unpaid.forEach(bill -> takenAmounts.add(bill.remaining()));
        outstanding.values().stream().filter(amount -> amount.signum() > 0).map(Money::rounded).forEach(takenAmounts::add);

        Map<BigDecimal, Set<String>> flatsByAmount = new HashMap<>();
        unpaid.forEach(bill -> flatsByAmount.computeIfAbsent(bill.remaining(), key -> new HashSet<>()).add(bill.code()));
        outstanding.forEach((code, amount) -> {
            if (amount.signum() > 0) {
                flatsByAmount.computeIfAbsent(Money.rounded(amount), key -> new HashSet<>()).add(code);
            }
        });

        Map<String, String> upiByFlat = new HashMap<>();
        jdbc.query("SELECT f.code, k.vpa FROM known_vpa k JOIN flat f ON f.id = k.flat_id ORDER BY f.code, k.vpa",
                (RowCallbackHandler) rs -> upiByFlat.putIfAbsent(rs.getString("code"), rs.getString("vpa")));

        List<UpiFlat> upiFlats = new ArrayList<>();
        Set<String> seenUpiFlats = new HashSet<>();
        for (BillRemaining bill : unpaid) {
            String upiId = upiByFlat.get(bill.code());
            if (upiId != null && seenUpiFlats.add(bill.code())) {
                upiFlats.add(new UpiFlat(bill.code(), upiId, bill.remaining(),
                        outstanding.getOrDefault(bill.code(), BigDecimal.ZERO)
                                .add(monthlyBill.get(bill.code()).multiply(BigDecimal.valueOf(settings.getAutoPostLimitMonths())))));
            }
        }

        List<UniqueBill> uniqueBills = unpaid.stream()
                .filter(bill -> flatsByAmount.get(bill.remaining()).size() == 1)
                .filter(bill -> !upiByFlat.containsKey(bill.code()))
                .map(bill -> new UniqueBill(bill.code(), bill.remaining()))
                .distinct()
                .toList();
        Optional<BigDecimal> sharedAmount = unpaid.stream()
                .map(BillRemaining::remaining)
                .filter(amount -> flatsByAmount.get(amount).size() > 1)
                .findFirst();

        List<GatewayPayment> unreconciledGateway = jdbc.query("""
                SELECT p.gateway_ref, p.amount FROM payment p
                WHERE p.gateway_ref IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM bank_row r WHERE r.gateway_ref = p.gateway_ref)
                ORDER BY p.paid_on DESC, p.id DESC LIMIT 2
                """, (rs, n) -> new GatewayPayment(rs.getString("gateway_ref"), Money.rounded(rs.getBigDecimal("amount"))));

        Optional<PostedUtr> postedUtr = jdbc.query("""
                SELECT p.utr, p.amount, f.code FROM payment p JOIN flat f ON f.id = p.flat_id
                WHERE p.utr IS NOT NULL ORDER BY p.paid_on DESC, p.id DESC LIMIT 1
                """, (rs, n) -> new PostedUtr(rs.getString("utr"), Money.rounded(rs.getBigDecimal("amount")),
                rs.getString("code"))).stream().findFirst();

        return new Facts(flatCodes, upiFlats, uniqueBills, sharedAmount, unreconciledGateway, postedUtr, takenAmounts);
    }

    private static byte[] toCsv(List<Line> lines) {
        StringBuilder csv = new StringBuilder("Txn Date,Value Date,Description,Ref No,Debit,Credit,Balance\n");
        BigDecimal balance = OPENING_BALANCE;
        for (Line line : lines) {
            if (line.debit() != null) {
                balance = balance.subtract(line.debit());
            }
            if (line.credit() != null) {
                balance = balance.add(line.credit());
            }
            String date = BANK_DATE.format(line.date());
            csv.append(date).append(',').append(date).append(',')
                    .append(line.description()).append(',')
                    .append(line.reference()).append(',')
                    .append(line.debit() == null ? "" : quoted(line.debit())).append(',')
                    .append(line.rawCredit() != null ? "\"" + line.rawCredit() + "\"" : line.credit() == null ? "" : quoted(line.credit()))
                    .append(',')
                    .append(quoted(balance)).append('\n');
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BYTE_ORDER_MARK);
        out.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String quoted(BigDecimal amount) {
        return "\"" + IndianRupees.format(amount).replace("₹", "") + "\"";
    }

    private record References(LocalDate date, long generation) {

        String utr(String purpose, String subject) {
            return reference("7", purpose + "|" + subject, 11);
        }

        String reference(String prefix, String seed, int digits) {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256")
                        .digest((date + "|" + generation + "|" + seed).getBytes(StandardCharsets.UTF_8));
                StringBuilder number = new StringBuilder(prefix);
                for (int index = 0; number.length() < prefix.length() + digits; index++) {
                    number.append(Math.floorMod(hash[index % hash.length] + index, 10));
                }
                return number.toString();
            } catch (NoSuchAlgorithmException unavailable) {
                throw new IllegalStateException("SHA-256 is not available", unavailable);
            }
        }
    }

    private record BillRemaining(String code, BigDecimal remaining) {
    }

    private record UpiFlat(String code, String upiId, BigDecimal oldestBillRemaining, BigDecimal autoPostLimit) {
    }

    private record UniqueBill(String code, BigDecimal remaining) {
    }

    private record GatewayPayment(String gatewayRef, BigDecimal amount) {
    }

    private record PostedUtr(String utr, BigDecimal amount, String code) {
    }

    private record Facts(List<String> flatCodes, List<UpiFlat> upiFlats, List<UniqueBill> uniqueBills,
                         Optional<BigDecimal> sharedAmount, List<GatewayPayment> unreconciledGateway,
                         Optional<PostedUtr> postedUtr, Set<BigDecimal> takenAmounts) {

        BigDecimal unusedAmount(String preferred) {
            BigDecimal amount = Money.of(preferred);
            while (takenAmounts.contains(amount)) {
                amount = amount.add(Money.of("1.00"));
            }
            takenAmounts.add(amount);
            return amount;
        }
    }

    private record Line(LocalDate date, String description, String reference, BigDecimal debit, BigDecimal credit,
                        String rawCredit) {

        static Line credit(LocalDate date, String description, String reference, BigDecimal amount) {
            return new Line(date, description, reference, null, amount, null);
        }

        static Line debit(LocalDate date, String description, String reference, BigDecimal amount) {
            return new Line(date, description, reference, amount, null, null);
        }

        static Line malformed(LocalDate date, String description, String reference, String rawCredit) {
            return new Line(date, description, reference, null, null, rawCredit);
        }
    }
}
