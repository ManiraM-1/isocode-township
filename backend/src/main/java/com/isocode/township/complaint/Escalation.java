package com.isocode.township.complaint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "escalation")
public class Escalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Column(name = "duty_manager_id", nullable = false)
    private Long dutyManagerId;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    protected Escalation() {
    }

    public Escalation(Long complaintId, Long dutyManagerId, String reason, Instant createdAt) {
        this.complaintId = complaintId;
        this.dutyManagerId = dutyManagerId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public void acknowledge(Long staffId, Instant at) {
        acknowledgedBy = staffId;
        acknowledgedAt = at;
    }

    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getComplaintId() {
        return complaintId;
    }

    public Long getDutyManagerId() {
        return dutyManagerId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }
}
