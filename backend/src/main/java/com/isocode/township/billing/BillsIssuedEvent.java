package com.isocode.township.billing;

import java.time.LocalDate;
import java.util.Set;

public record BillsIssuedEvent(LocalDate issuedOn, Set<Long> flatIds) {
}
