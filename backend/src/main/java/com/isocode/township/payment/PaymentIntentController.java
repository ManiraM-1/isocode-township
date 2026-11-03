package com.isocode.township.payment;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.payment.dto.PaymentIntentRequest;
import com.isocode.township.payment.dto.PaymentIntentResponse;
import com.isocode.township.payment.dto.SimulateRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentIntentController {

    private final PaymentIntentService intentService;

    public PaymentIntentController(PaymentIntentService intentService) {
        this.intentService = intentService;
    }

    @PostMapping("/api/flats/{flatId}/payment-intents")
    public PaymentIntentResponse create(@AuthenticationPrincipal SessionUser user, @PathVariable Long flatId,
                                        @Valid @RequestBody PaymentIntentRequest body) {
        return intentService.create(user, flatId, body.amount(), body.idempotencyKey());
    }

    @PostMapping("/api/payment-intents/{intentId}/simulate")
    public PaymentIntentResponse simulate(@AuthenticationPrincipal SessionUser user, @PathVariable Long intentId,
                                          @Valid @RequestBody SimulateRequest body) {
        return intentService.simulate(user, intentId, body.outcome());
    }
}
