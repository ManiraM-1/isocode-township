package com.isocode.township.complaint;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.complaint.dto.ComplaintDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/desk/escalations")
public class DeskEscalationController {

    private final DeskComplaintService deskService;
    private final ComplaintViews views;

    public DeskEscalationController(DeskComplaintService deskService, ComplaintViews views) {
        this.deskService = deskService;
        this.views = views;
    }

    @PostMapping("/{escalationId}/acknowledge")
    public ComplaintDetail acknowledge(@AuthenticationPrincipal SessionUser staff, @PathVariable Long escalationId) {
        return views.detail(deskService.acknowledgeEscalation(staff, escalationId), staff);
    }
}
