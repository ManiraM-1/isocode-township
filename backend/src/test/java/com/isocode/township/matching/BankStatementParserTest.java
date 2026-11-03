package com.isocode.township.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BankStatementParserTest {

    private static final String STANDARD_HEADER = "Txn Date,Description,Ref No,Debit,Credit,Balance\n";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "Txn Date,Description,Ref No,Debit,Credit",
            "Transaction Date,Narration,Reference,Withdrawal,Deposit",
            "Date,Particulars,Chq/Ref No,Dr Amount,Cr Amount",
            "Value Date,Remarks,UTR,Debit,Credit Amount",
            "Date,Narration,Cheque No,Withdrawal,Deposit"
    })
    void everyHeaderAliasSetIsRecognised(String header) {
        ParsedStatement statement = parse(header + "\n03/11/2026,UPI/CR/T21-1601,630112457788,,\"5,660.00\"\n");

        assertThat(statement.errors()).isEmpty();
        assertThat(statement.rows()).singleElement().satisfies(row -> {
            assertThat(row.txnDate()).isEqualTo(LocalDate.of(2026, 11, 3));
            assertThat(row.description()).isEqualTo("UPI/CR/T21-1601");
            assertThat(row.reference()).isEqualTo("630112457788");
            assertThat(row.credit()).isEqualTo(new BigDecimal("5660.00"));
            assertThat(row.kind()).isEqualTo(RowKind.CREDIT);
        });
    }

    @Test
    void headersIgnoreCaseAndSpacingAndExtraColumns() {
        ParsedStatement statement = parse("  TXN  DATE , narration ,Sl No, ref no ,CREDIT,Balance\n"
                + "03/11/2026,Maintenance,1,REF1,100.00,999.00\n");

        assertThat(statement.rows()).extracting(StatementRow::description, StatementRow::credit)
                .containsExactly(tuple("Maintenance", new BigDecimal("100.00")));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = ';', value = {
            "\"1,23,456.00\"; 123456.00",
            "\"₹ 10,660.00\"; 10660.00",
            "Rs. 6370; 6370.00",
            "INR 5660.5; 5660.50",
            "\"12,34,56,789.99\"; 123456789.99"
    })
    void amountsAcceptIndianGroupingAndRupeeSymbols(String rawCredit, String expected) {
        ParsedStatement statement = parse(STANDARD_HEADER + "03/11/2026,Maintenance,R1,," + rawCredit + ",\n");

        assertThat(statement.errors()).isEmpty();
        assertThat(statement.rows().getFirst().credit()).isEqualTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"03/11/2026", "3/11/2026", "03-11-2026", "2026-11-03"})
    void eachAcceptedDateFormatParses(String date) {
        ParsedStatement statement = parse(STANDARD_HEADER + date + ",Maintenance,R1,,100.00,\n");

        assertThat(statement.rows().getFirst().txnDate()).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void malformedRowsAreReportedWithTheirSpreadsheetRowNumberAndOthersStillParse() {
        ParsedStatement statement = parse(STANDARD_HEADER
                + "01/11/2026,Good row,R1,,100.00,\n"
                + "02/11/2026,Bad amount,R2,,\"6,19O.00\",\n"
                + "31/11/2026,Bad date,R3,,100.00,\n"
                + "03/11/2026,Good row too,R4,,200.00,\n");

        assertThat(statement.rows()).extracting(StatementRow::rowNo).containsExactly(2, 5);
        assertThat(statement.errors()).extracting(RowError::rowNo).containsExactly(3, 4);
        assertThat(statement.errors().get(0).message())
                .isEqualTo("Row 3: credit amount '6,19O.00' is not an amount like 1,23,456.00");
        assertThat(statement.errors().get(1).message()).startsWith("Row 4: '31/11/2026' is not a date");
        assertThat(statement.totalRows()).isEqualTo(4);
    }

    @Test
    void rowWithBothCreditAndDebitIsAnError() {
        ParsedStatement statement = parse(STANDARD_HEADER + "03/11/2026,Odd,R1,50.00,100.00,\n");

        assertThat(statement.errors()).extracting(RowError::message)
                .containsExactly("Row 2: a row cannot have both a credit and a debit amount");
    }

    @Test
    void amountsWithMoreThanTwoDecimalsAreErrors() {
        ParsedStatement statement = parse(STANDARD_HEADER + "03/11/2026,Odd,R1,,100.005,\n");

        assertThat(statement.errors()).hasSize(1);
    }

    @Test
    void debitOnlyAndZeroRowsAreReturnedAsNonCredit() {
        ParsedStatement statement = parse(STANDARD_HEADER
                + "01/11/2026,NEFT DR-MSEDCL,N1,\"1,25,640.00\",,\n"
                + "02/11/2026,Balance enquiry,,,0.00,\n"
                + "03/11/2026,Maintenance,R1,,100.00,\n");

        assertThat(statement.rows()).extracting(StatementRow::kind, StatementRow::debit)
                .containsExactly(
                        tuple(RowKind.NON_CREDIT, new BigDecimal("125640.00")),
                        tuple(RowKind.NON_CREDIT, null),
                        tuple(RowKind.CREDIT, null));
    }

    @Test
    void fileWithAByteOrderMarkParses() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = (STANDARD_HEADER + "03/11/2026,Maintenance,R1,,100.00,\n").getBytes(StandardCharsets.UTF_8);
        byte[] file = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, file, 0, bom.length);
        System.arraycopy(body, 0, file, bom.length, body.length);

        ParsedStatement statement = BankStatementParser.parse(new ByteArrayInputStream(file));

        assertThat(statement.errors()).isEmpty();
        assertThat(statement.rows()).hasSize(1);
    }

    @Test
    void missingRequiredHeaderFailsTheWholeFileWithAClearMessage() {
        assertThatThrownBy(() -> parse("Txn Date,Ref No,Debit,Credit\n03/11/2026,R1,,100.00\n"))
                .isInstanceOf(BankStatementFormatException.class)
                .hasMessage("The statement is missing a required column: "
                        + "description (one of Description, Narration, Particulars, Remarks).");
    }

    @Test
    void optionalColumnsMayBeAbsent() {
        ParsedStatement statement = parse("Date,Narration,Credit\n03/11/2026,Maintenance,100.00\n");

        assertThat(statement.rows()).singleElement()
                .satisfies(row -> assertThat(row.reference()).isEmpty());
    }

    @Test
    void blankLinesAreSkippedButStillCountForRowNumbers() {
        ParsedStatement statement = parse(STANDARD_HEADER
                + "01/11/2026,First,R1,,100.00,\n"
                + "\n"
                + "03/11/2026,Third line,R3,,x,\n");

        assertThat(statement.rows()).extracting(StatementRow::rowNo).containsExactly(2);
        assertThat(statement.errors()).extracting(RowError::rowNo).containsExactly(4);
    }

    @Test
    void brokenCsvFailsTheWholeFile() {
        assertThatThrownBy(() -> parse(STANDARD_HEADER + "03/11/2026,\"unterminated,R1,,100.00,\n"))
                .isInstanceOf(BankStatementFormatException.class);
    }

    @Test
    void moreThanTheRowLimitFailsTheWholeFile() {
        String rows = IntStream.rangeClosed(1, BankStatementParser.MAX_ROWS + 1)
                .mapToObj(i -> "03/11/2026,Row " + i + ",R" + i + ",,1.00,")
                .collect(Collectors.joining("\n"));

        assertThatThrownBy(() -> parse(STANDARD_HEADER + rows))
                .isInstanceOf(BankStatementFormatException.class)
                .hasMessageContaining("more than 20000 rows");
    }

    @Test
    void referencesAreExtractedFromDescriptionAndReference() {
        ParsedStatement statement = parse(STANDARD_HEADER
                + "01/11/2026,UPI/CR/630112457788/AAMIR SHAIKH/Aamir.S@OkIcici/T21-1601,,,\"5,660.00\",\n"
                + "02/11/2026,NEFT CR-HDFC0001234-SWATI JOSHI,N305260011122233,,6370.00,\n"
                + "03/11/2026,PG SETTLEMENT pgtest-jd9ep9vau8cp,PGTEST-JD9EP9VAU8CP,,3000.00,\n"
                + "03/11/2026,Cash deposit,,,500.00,\n");

        assertThat(statement.rows()).extracting(StatementRow::utr, StatementRow::upiId, StatementRow::gatewayRef)
                .containsExactly(
                        tuple(Optional.of("630112457788"), Optional.of("aamir.s@okicici"),
                                Optional.empty()),
                        tuple(Optional.of("N305260011122233"), Optional.empty(),
                                Optional.empty()),
                        tuple(Optional.empty(), Optional.empty(),
                                Optional.of("PGTEST-JD9EP9VAU8CP")),
                        tuple(Optional.empty(), Optional.empty(), Optional.empty()));
    }

    @Test
    void rowHashIgnoresCaseAndSpacingButNotTheAmountOrDate() {
        LocalDate day = LocalDate.of(2026, 11, 3);
        String hash = BankStatementParser.rowHash(day, "UPI/CR  Maintenance", "ref1", new BigDecimal("100.00"));

        assertThat(BankStatementParser.rowHash(day, "upi/cr maintenance", "REF1 ", new BigDecimal("100.00")))
                .isEqualTo(hash);
        assertThat(BankStatementParser.rowHash(day, "UPI/CR Maintenance", "REF1", new BigDecimal("100.01")))
                .isNotEqualTo(hash);
        assertThat(BankStatementParser.rowHash(day.plusDays(1), "UPI/CR Maintenance", "REF1", new BigDecimal("100.00")))
                .isNotEqualTo(hash);
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    private static ParsedStatement parse(String csv) {
        return BankStatementParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }
}
