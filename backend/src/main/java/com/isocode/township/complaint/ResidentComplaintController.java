package com.isocode.township.complaint;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.common.FieldValidationException;
import com.isocode.township.common.NotFoundException;
import com.isocode.township.complaint.ComplaintPhotoStore.StoredPhoto;
import com.isocode.township.complaint.dto.CloseRequest;
import com.isocode.township.complaint.dto.ComplaintDetail;
import com.isocode.township.complaint.dto.ComplaintSummary;
import com.isocode.township.complaint.dto.ReopenRequest;
import com.isocode.township.flat.FlatAccessGuard;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ResidentComplaintController {

    private final ComplaintService complaintService;
    private final ComplaintRepository complaintRepository;
    private final ComplaintPhotoStore photoStore;
    private final ComplaintViews views;
    private final FlatAccessGuard accessGuard;

    public ResidentComplaintController(ComplaintService complaintService, ComplaintRepository complaintRepository,
                                       ComplaintPhotoStore photoStore, ComplaintViews views,
                                       FlatAccessGuard accessGuard) {
        this.complaintService = complaintService;
        this.complaintRepository = complaintRepository;
        this.photoStore = photoStore;
        this.views = views;
        this.accessGuard = accessGuard;
    }

    @PostMapping(path = "/api/flats/{flatId}/complaints", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ComplaintDetail raise(@AuthenticationPrincipal SessionUser user, @PathVariable Long flatId,
                                 @RequestParam(value = "description", required = false) String description,
                                 @RequestParam(value = "photo", required = false) MultipartFile photo) {
        Complaint complaint = complaintService.raise(user, flatId, description, bytesOf(photo));
        return views.detail(complaint, user);
    }

    @GetMapping("/api/flats/{flatId}/complaints")
    public List<ComplaintSummary> flatComplaints(@AuthenticationPrincipal SessionUser user, @PathVariable Long flatId) {
        accessGuard.requireMemberOrStaff(user, flatId);
        return complaintRepository.findByFlatIdOrderByCreatedAtDescIdDesc(flatId).stream().map(views::summary).toList();
    }

    @GetMapping("/api/complaints/{complaintId}")
    public ComplaintDetail detail(@AuthenticationPrincipal SessionUser user, @PathVariable Long complaintId) {
        return views.detail(complaintService.viewable(user, complaintId), user);
    }

    @GetMapping("/api/complaints/{complaintId}/photo")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal SessionUser user, @PathVariable Long complaintId) {
        complaintService.viewable(user, complaintId);
        StoredPhoto photo = photoStore.load(complaintId).orElseThrow(() -> new NotFoundException("No photo"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(photo.bytes());
    }

    @PostMapping("/api/complaints/{complaintId}/close")
    public ComplaintDetail close(@AuthenticationPrincipal SessionUser user, @PathVariable Long complaintId,
                                 @Valid @RequestBody CloseRequest body) {
        return views.detail(complaintService.close(user, complaintId, body.rating(), body.comment()), user);
    }

    @PostMapping("/api/complaints/{complaintId}/reopen")
    public ComplaintDetail reopen(@AuthenticationPrincipal SessionUser user, @PathVariable Long complaintId,
                                  @Valid @RequestBody ReopenRequest body) {
        return views.detail(complaintService.reopen(user, complaintId, body.reason()), user);
    }

    private static byte[] bytesOf(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        try {
            return photo.getBytes();
        } catch (IOException unreadable) {
            throw new FieldValidationException("photo", "The photo could not be read");
        }
    }
}
