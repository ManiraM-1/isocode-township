package com.isocode.township.matching.dto;

import java.util.List;

public record QueueActionResponse(BankRowView row, List<String> warnings) {
}
