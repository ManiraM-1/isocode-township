package com.isocode.township.billing;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.billing.dto.BillResponse;
import com.isocode.township.billing.dto.FlatSummaryResponse;
import com.isocode.township.billing.dto.LedgerLineResponse;
import com.isocode.township.flat.FlatAccessGuard;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flats/{flatId}")
public class FlatBillingController {

    private final FlatAccessGuard accessGuard;
    private final FlatStatementService statementService;

    public FlatBillingController(FlatAccessGuard accessGuard, FlatStatementService statementService) {
        this.accessGuard = accessGuard;
        this.statementService = statementService;
    }

    @GetMapping("/summary")
    public FlatSummaryResponse summary(@AuthenticationPrincipal SessionUser user, @PathVariable Long flatId) {
        accessGuard.requireMemberOrStaff(user, flatId);
        return statementService.summary(flatId);
    }

    @GetMapping("/bills")
    public List<BillResponse> bills(@AuthenticationPrincipal SessionUser user, @PathVariable Long flatId) {
        accessGuard.requireMemberOrStaff(user, flatId);
        return statementService.bills(flatId);
    }

    @GetMapping("/ledger")
    public List<LedgerLineResponse> ledger(@AuthenticationPrincipal SessionUser user, @PathVariable Long flatId) {
        accessGuard.requireMemberOrStaff(user, flatId);
        return statementService.ledger(flatId);
    }
}
