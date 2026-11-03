package com.isocode.township.matching;

import java.util.List;
import java.util.Locale;

public enum StatementColumn {
    DATE(true, "Txn Date", "Transaction Date", "Date", "Value Date"),
    DESCRIPTION(true, "Description", "Narration", "Particulars", "Remarks"),
    REFERENCE(false, "Ref No", "Reference", "Chq/Ref No", "UTR", "Cheque No"),
    CREDIT(true, "Credit", "Deposit", "Cr Amount", "Credit Amount"),
    DEBIT(false, "Debit", "Withdrawal", "Dr Amount");

    private final boolean required;
    private final List<String> aliases;

    StatementColumn(boolean required, String... aliases) {
        this.required = required;
        this.aliases = List.of(aliases);
    }

    public boolean isRequired() {
        return required;
    }

    public List<String> aliases() {
        return aliases;
    }

    public List<String> normalisedAliases() {
        return aliases.stream().map(StatementColumn::normaliseHeader).toList();
    }

    public static String normaliseHeader(String header) {
        return header.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
