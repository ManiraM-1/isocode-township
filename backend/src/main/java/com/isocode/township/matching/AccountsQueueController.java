package com.isocode.township.matching;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.matching.AccountsQueueService.Resolved;
import com.isocode.township.matching.dto.AssignRequest;
import com.isocode.township.matching.dto.IgnoreRequest;
import com.isocode.township.matching.dto.QueueActionResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/desk/bank-rows/{rowId}")
public class AccountsQueueController {

    private final AccountsQueueService queueService;
    private final BankImportViews views;

    public AccountsQueueController(AccountsQueueService queueService, BankImportViews views) {
        this.queueService = queueService;
        this.views = views;
    }

    @PostMapping("/confirm")
    public QueueActionResponse confirm(@AuthenticationPrincipal SessionUser user, @PathVariable Long rowId) {
        return respond(queueService.confirm(rowId, user.userId()));
    }

    @PostMapping("/assign")
    public QueueActionResponse assign(@AuthenticationPrincipal SessionUser user, @PathVariable Long rowId,
                                      @Valid @RequestBody AssignRequest body) {
        return respond(queueService.assign(rowId, body.flatId(), body.note(), user.userId()));
    }

    @PostMapping("/ignore")
    public QueueActionResponse ignore(@AuthenticationPrincipal SessionUser user, @PathVariable Long rowId,
                                      @Valid @RequestBody IgnoreRequest body) {
        return respond(queueService.ignore(rowId, body.reason(), user.userId()));
    }

    private QueueActionResponse respond(Resolved resolved) {
        return new QueueActionResponse(views.row(resolved.row()), resolved.warnings());
    }
}
