package com.isocode.township.matching.dto;

import java.util.List;

public record BankImportDetails(BankImportSummary summary, List<BankRowView> rows) {
}
