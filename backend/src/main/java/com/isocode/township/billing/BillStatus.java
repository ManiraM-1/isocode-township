package com.isocode.township.billing;

import java.time.LocalDate;

public enum BillStatus {
    PAID,
    DUE,
    OVERDUE;

    public static BillStatus of(Bill bill, LocalDate today) {
        if (bill.isFullyPaid()) {
            return PAID;
        }
        return today.isAfter(bill.getDueDate()) ? OVERDUE : DUE;
    }
}
