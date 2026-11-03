package com.isocode.township.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "known_vpa")
public class KnownUpiId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vpa", nullable = false, unique = true, length = 100)
    private String upiId;

    @Column(name = "flat_id", nullable = false)
    private Long flatId;

    @Column(name = "learned_from_row_id")
    private Long learnedFromRowId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KnownUpiId() {
    }

    public KnownUpiId(String upiId, Long flatId, Long learnedFromRowId, Instant createdAt) {
        this.upiId = upiId.toLowerCase(Locale.ROOT);
        this.flatId = flatId;
        this.learnedFromRowId = learnedFromRowId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUpiId() {
        return upiId;
    }

    public Long getFlatId() {
        return flatId;
    }

    public Long getLearnedFromRowId() {
        return learnedFromRowId;
    }
}
