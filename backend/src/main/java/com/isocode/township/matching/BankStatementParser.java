package com.isocode.township.matching;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;

public final class BankStatementParser {

    public static final int MAX_ROWS = 20_000;

    private static final char BYTE_ORDER_MARK = '﻿';
    private static final int HEADER_ROWS = 1;
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(false)
            .setIgnoreSurroundingSpaces(true)
            .setAllowMissingColumnNames(true)
            .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL)
            .get();

    private BankStatementParser() {
    }

    public static ParsedStatement parse(InputStream input) {
        try (CSVParser parser = FORMAT.parse(withoutByteOrderMark(input))) {
            Map<StatementColumn, Integer> columns = resolveColumns(parser.getHeaderNames());
            List<StatementRow> rows = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            int dataRows = 0;
            for (CSVRecord record : parser) {
                if (isBlank(record)) {
                    continue;
                }
                dataRows++;
                int rowNo = (int) record.getRecordNumber() + HEADER_ROWS;
                if (dataRows > MAX_ROWS) {
                    throw new BankStatementFormatException(
                            "The statement has more than " + MAX_ROWS + " rows. Split it into smaller files.");
                }
                try {
                    rows.add(toRow(rowNo, record, columns));
                } catch (IllegalArgumentException invalid) {
                    errors.add(new RowError(rowNo, "Row " + rowNo + ": " + invalid.getMessage()));
                }
            }
            return new ParsedStatement(List.copyOf(rows), List.copyOf(errors));
        } catch (IOException | UncheckedIOException unreadable) {
            throw new BankStatementFormatException("The file could not be read as a CSV bank statement.");
        } catch (IllegalStateException | IllegalArgumentException malformed) {
            throw new BankStatementFormatException("The file is not a valid CSV: " + malformed.getMessage());
        }
    }

    private static StatementRow toRow(int rowNo, CSVRecord record, Map<StatementColumn, Integer> columns) {
        LocalDate txnDate = StatementDates.parse(cell(record, columns, StatementColumn.DATE));
        String description = cell(record, columns, StatementColumn.DESCRIPTION).replaceAll("\\s+", " ").strip();
        String reference = cell(record, columns, StatementColumn.REFERENCE).strip();
        BigDecimal credit = amount(record, columns, StatementColumn.CREDIT, "credit");
        BigDecimal debit = amount(record, columns, StatementColumn.DEBIT, "debit");

        boolean isCredit = credit != null && credit.signum() > 0;
        if (isCredit && debit != null && debit.signum() > 0) {
            throw new IllegalArgumentException("a row cannot have both a credit and a debit amount");
        }
        RowKind kind = isCredit ? RowKind.CREDIT : RowKind.NON_CREDIT;

        return new StatementRow(rowNo, txnDate, description, reference, credit, debit, kind,
                PaymentReferences.utr(description, reference),
                PaymentReferences.upiId(description, reference),
                PaymentReferences.gatewayRef(description, reference),
                rowHash(txnDate, description, reference, credit));
    }

    private static BigDecimal amount(CSVRecord record, Map<StatementColumn, Integer> columns, StatementColumn column,
                                     String label) {
        try {
            return StatementAmounts.parseOrNull(cell(record, columns, column));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(label + " amount " + invalid.getMessage());
        }
    }

    private static String cell(CSVRecord record, Map<StatementColumn, Integer> columns, StatementColumn column) {
        Integer index = columns.get(column);
        if (index == null || index >= record.size()) {
            return "";
        }
        return record.get(index);
    }

    private static Map<StatementColumn, Integer> resolveColumns(List<String> headerNames) {
        List<String> normalised = headerNames.stream().map(StatementColumn::normaliseHeader).toList();
        Map<StatementColumn, Integer> columns = new EnumMap<>(StatementColumn.class);
        List<StatementColumn> missing = new ArrayList<>();
        for (StatementColumn column : StatementColumn.values()) {
            column.normalisedAliases().stream()
                    .map(normalised::indexOf)
                    .filter(index -> index >= 0)
                    .findFirst()
                    .ifPresentOrElse(index -> columns.put(column, index), () -> {
                        if (column.isRequired()) {
                            missing.add(column);
                        }
                    });
        }
        if (!missing.isEmpty()) {
            throw new BankStatementFormatException("The statement is missing a required column: " + missing.stream()
                    .map(column -> column.name().toLowerCase(Locale.ROOT) + " (one of " + String.join(", ", column.aliases()) + ")")
                    .collect(Collectors.joining("; ")) + ".");
        }
        return columns;
    }

    private static boolean isBlank(CSVRecord record) {
        return record.stream().allMatch(value -> value == null || value.isBlank());
    }

    private static Reader withoutByteOrderMark(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        reader.mark(1);
        if (reader.read() != BYTE_ORDER_MARK) {
            reader.reset();
        }
        return reader;
    }

    static String rowHash(LocalDate txnDate, String description, String reference, BigDecimal credit) {
        String canonical = String.join("|",
                txnDate.toString(),
                description.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip(),
                reference.toUpperCase(Locale.ROOT).strip(),
                credit == null ? "0.00" : credit.toPlainString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is not available", unavailable);
        }
    }
}
