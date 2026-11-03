package com.isocode.township.complaint;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.complaint.dto.AssigneeChangeRequest;
import com.isocode.township.complaint.dto.CommentRequest;
import com.isocode.township.complaint.dto.ComplaintDetail;
import com.isocode.township.complaint.dto.ComplaintSummary;
import com.isocode.township.complaint.dto.StatusChangeRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/desk/complaints")
public class DeskComplaintController {

    private final DeskComplaintService deskService;
    private final ComplaintService complaintService;
    private final ComplaintViews views;

    public DeskComplaintController(DeskComplaintService deskService, ComplaintService complaintService,
                                   ComplaintViews views) {
        this.deskService = deskService;
        this.complaintService = complaintService;
        this.views = views;
    }

    @GetMapping
    public List<ComplaintSummary> board(@RequestParam(required = false) DeskLane lane,
                                        @RequestParam(required = false) ComplaintStatus status,
                                        @RequestParam(required = false) ComplaintCategory category,
                                        @RequestParam(required = false) Long assigneeId,
                                        @RequestParam(required = false) String tower) {
        return deskService.board(lane, status, category, assigneeId, tower).stream().map(views::summary).toList();
    }

    @GetMapping("/{complaintId}")
    public ComplaintDetail detail(@AuthenticationPrincipal SessionUser staff, @PathVariable Long complaintId) {
        return views.detail(complaintService.viewable(staff, complaintId), staff);
    }

    @PatchMapping("/{complaintId}/status")
    public ComplaintDetail changeStatus(@AuthenticationPrincipal SessionUser staff, @PathVariable Long complaintId,
                                        @Valid @RequestBody StatusChangeRequest body) {
        return views.detail(deskService.changeStatus(staff, complaintId, body.status(), body.note()), staff);
    }

    @PatchMapping("/{complaintId}/assignee")
    public ComplaintDetail reassign(@AuthenticationPrincipal SessionUser staff, @PathVariable Long complaintId,
                                    @Valid @RequestBody AssigneeChangeRequest body) {
        return views.detail(deskService.reassign(staff, complaintId, body.assigneeId(), body.note()), staff);
    }

    @PostMapping("/{complaintId}/comments")
    public ComplaintDetail comment(@AuthenticationPrincipal SessionUser staff, @PathVariable Long complaintId,
                                   @Valid @RequestBody CommentRequest body) {
        return views.detail(deskService.comment(staff, complaintId, body.text()), staff);
    }
}
