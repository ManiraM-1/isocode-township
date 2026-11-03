package com.isocode.township.payment;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.payment.dto.ReceiptResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/api/receipts/{receiptNo}")
    public ReceiptResponse receipt(@AuthenticationPrincipal SessionUser user, @PathVariable String receiptNo) {
        return receiptService.byNumber(user, receiptNo);
    }
}
