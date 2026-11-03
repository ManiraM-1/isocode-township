package com.isocode.township.payment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class ReceiptNumbers {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final PaymentRepository paymentRepository;

    public ReceiptNumbers(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public String next(LocalDate paidOn) {
        return "RCPT-%s-%05d".formatted(YEAR_MONTH.format(paidOn), paymentRepository.nextReceiptSequence());
    }
}
