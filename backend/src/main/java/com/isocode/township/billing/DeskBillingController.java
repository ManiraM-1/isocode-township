package com.isocode.township.billing;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.billing.dto.BillingRunRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeskBillingController {

    private final BillingRunService billingRunService;

    public DeskBillingController(BillingRunService billingRunService) {
        this.billingRunService = billingRunService;
    }

    @PostMapping("/api/desk/billing-runs")
    public BillingRunResult run(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody BillingRunRequest body) {
        return billingRunService.run(body.month(), user.userId());
    }
}
