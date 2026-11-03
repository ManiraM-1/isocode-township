package com.isocode.township.common;

import java.math.BigDecimal;

public final class IndianRupees {

    private IndianRupees() {
    }

    public static String format(BigDecimal amount) {
        BigDecimal rounded = Money.rounded(amount);
        String sign = rounded.signum() < 0 ? "-" : "";
        String plain = rounded.abs().toPlainString();
        int dot = plain.indexOf('.');
        String whole = plain.substring(0, dot);
        String paise = plain.substring(dot);
        return sign + "₹" + groupIndian(whole) + paise;
    }

    private static String groupIndian(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        String lastThree = digits.substring(digits.length() - 3);
        StringBuilder head = new StringBuilder(digits.substring(0, digits.length() - 3));
        for (int position = head.length() - 2; position > 0; position -= 2) {
            head.insert(position, ',');
        }
        return head + "," + lastThree;
    }
}
