package com.isocode.township.complaint;

import com.isocode.township.complaint.ComplaintStateMachine.Actor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "complaint")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ref_no", nullable = false, unique = true, length = 20)
    private String refNo;

    @Column(name = "flat_id", nullable = false)
    private Long flatId;

    @Column(name = "raised_by", nullable = false)
    private Long raisedBy;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "photo_content_type", length = 20)
    private String photoContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ComplaintCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_category", length = 16)
    private ComplaintCategory aiCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_urgency", length = 8)
    private Urgency aiUrgency;

    @Column(name = "ai_confidence", precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    @Column(name = "ai_summary", length = 200)
    private String aiSummary;

    @Column(name = "ai_reason", length = 300)
    private String aiReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "classified_by", nullable = false, length = 16)
    private ClassifiedBy classifiedBy;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "safety_escalated", nullable = false)
    private boolean safetyEscalated;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "safety_terms", nullable = false, columnDefinition = "text[]")
    private String[] safetyTerms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ComplaintStatus status;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "first_response_due_at", nullable = false)
    private Instant firstResponseDueAt;

    @Column(name = "resolve_due_at", nullable = false)
    private Instant resolveDueAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount;

    @Column
    private Short rating;

    @Column(name = "rating_comment", length = 500)
    private String ratingComment;

    protected Complaint() {
    }

    public Complaint(String refNo, Long flatId, Long raisedBy, String description, Classification classification,
                     Instant createdAt, Instant firstResponseDueAt, Instant resolveDueAt) {
        this.refNo = refNo;
        this.flatId = flatId;
        this.raisedBy = raisedBy;
        this.description = description;
        this.category = classification.category();
        this.urgency = classification.urgency();
        this.aiCategory = classification.aiCategory();
        this.aiUrgency = classification.aiUrgency();
        this.aiConfidence = classification.aiConfidence();
        this.aiSummary = classification.aiSummary();
        this.aiReason = classification.aiReason();
        this.classifiedBy = classification.classifiedBy();
        this.needsReview = classification.needsReview();
        this.safetyTerms = classification.safetyTerms().toArray(String[]::new);
        this.safetyEscalated = !classification.safetyTerms().isEmpty();
        this.status = ComplaintStatus.OPEN;
        this.createdAt = createdAt;
        this.firstResponseDueAt = firstResponseDueAt;
        this.resolveDueAt = resolveDueAt;
    }

    public void assignTo(Long staffId) {
        assigneeId = staffId;
        if (status == ComplaintStatus.OPEN) {
            status = ComplaintStatus.ASSIGNED;
        }
    }

    public void moveTo(ComplaintStatus target, Actor actor, Instant now) {
        ComplaintStateMachine.requireMove(status, target, actor);
        status = target;
        if (target == ComplaintStatus.RESOLVED) {
            resolvedAt = now;
        }
    }

    public void close(int stars, String comment, Instant now) {
        ComplaintStateMachine.requireMove(status, ComplaintStatus.CLOSED, Actor.RESIDENT);
        status = ComplaintStatus.CLOSED;
        rating = (short) stars;
        ratingComment = comment;
        closedAt = now;
    }

    public void reopen(Instant newResolveDue) {
        ComplaintStateMachine.requireMove(status, ComplaintStatus.ASSIGNED, Actor.RESIDENT);
        status = ComplaintStatus.ASSIGNED;
        reopenCount++;
        resolvedAt = null;
        resolveDueAt = newResolveDue;
    }

    public boolean recordFirstResponse(Instant now) {
        if (firstResponseAt != null) {
            return false;
        }
        firstResponseAt = now;
        return true;
    }

    public void attachPhotoType(String contentType) {
        photoContentType = contentType;
    }

    public Long getId() {
        return id;
    }

    public String getRefNo() {
        return refNo;
    }

    public Long getFlatId() {
        return flatId;
    }

    public Long getRaisedBy() {
        return raisedBy;
    }

    public String getDescription() {
        return description;
    }

    public Optional<String> getPhotoContentType() {
        return Optional.ofNullable(photoContentType);
    }

    public ComplaintCategory getCategory() {
        return category;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public ComplaintCategory getAiCategory() {
        return aiCategory;
    }

    public Urgency getAiUrgency() {
        return aiUrgency;
    }

    public BigDecimal getAiConfidence() {
        return aiConfidence;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public String getAiReason() {
        return aiReason;
    }

    public ClassifiedBy getClassifiedBy() {
        return classifiedBy;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public boolean isSafetyEscalated() {
        return safetyEscalated;
    }

    public List<String> getSafetyTerms() {
        return List.of(safetyTerms);
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public Long getParentId() {
        return parentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFirstResponseAt() {
        return firstResponseAt;
    }

    public Instant getFirstResponseDueAt() {
        return firstResponseDueAt;
    }

    public Instant getResolveDueAt() {
        return resolveDueAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public int getReopenCount() {
        return reopenCount;
    }

    public Optional<Integer> getRating() {
        return Optional.ofNullable(rating).map(Short::intValue);
    }

    public String getRatingComment() {
        return ratingComment;
    }

    public record Classification(ComplaintCategory category, Urgency urgency, ComplaintCategory aiCategory,
                                 Urgency aiUrgency, BigDecimal aiConfidence, String aiSummary, String aiReason,
                                 ClassifiedBy classifiedBy, boolean needsReview, List<String> safetyTerms) {

        public Classification {
            safetyTerms = safetyTerms == null ? List.of() : List.copyOf(safetyTerms);
        }
    }
}
