package com.isocode.township.committee.dto;

import com.isocode.township.committee.DuesAgeing.AgedFlat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record AgeingFlatsResponse(YearMonth month, LocalDate asOf, String bucket, String label, int flats,
                                  BigDecimal amount, List<AgedFlat> items) {
}
