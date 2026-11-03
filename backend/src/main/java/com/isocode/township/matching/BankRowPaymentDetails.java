package com.isocode.township.matching;

import com.isocode.township.payment.PaymentMethod;
import com.isocode.township.payment.PaymentSource;
import com.isocode.township.payment.PostPaymentCommand;

final class BankRowPaymentDetails {

    private BankRowPaymentDetails() {
    }

    static PostPaymentCommand commandFor(BankRow row, Long flatId, Long actorUserId) {
        PaymentMethod method = row.getVpa().isPresent() ? PaymentMethod.UPI : PaymentMethod.BANK_TRANSFER;
        return new PostPaymentCommand(flatId, row.getAmount(), method, PaymentSource.BANK_STATEMENT, null,
                row.getUtr().orElse(null), row.getTxnDate(), actorUserId);
    }
}
