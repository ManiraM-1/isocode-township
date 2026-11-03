package com.isocode.township.payment;

final class PaymentDescriptions {

    private PaymentDescriptions() {
    }

    static String ledgerCredit(PaymentMethod method, String receiptNo) {
        return "Payment received by " + label(method) + ", receipt " + receiptNo;
    }

    static String label(PaymentMethod method) {
        return switch (method) {
            case ONLINE_TEST -> "online payment (test mode)";
            case UPI -> "UPI";
            case BANK_TRANSFER -> "bank transfer";
            case CHEQUE -> "cheque";
        };
    }
}
