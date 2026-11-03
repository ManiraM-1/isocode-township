package com.isocode.township.complaint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "complaint_event")
public class ComplaintEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ComplaintEventType type;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "from_value", length = 40)
    private String fromValue;

    @Column(name = "to_value", length = 40)
    private String toValue;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ComplaintEvent() {
    }

    public ComplaintEvent(Long complaintId, ComplaintEventType type, Long actorUserId, String fromValue,
                          String toValue, String note, Instant createdAt) {
        this.complaintId = complaintId;
        this.type = type;
        this.actorUserId = actorUserId;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getComplaintId() {
        return complaintId;
    }

    public ComplaintEventType getType() {
        return type;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getFromValue() {
        return fromValue;
    }

    public String getToValue() {
        return toValue;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
