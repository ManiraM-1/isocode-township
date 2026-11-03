package com.isocode.township.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "society_settings")
public class SocietySettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "rate_per_sqft", nullable = false, precision = 8, scale = 2)
    private BigDecimal ratePerSqft;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(name = "late_interest_pct_pa", nullable = false, precision = 5, scale = 2)
    private BigDecimal lateInterestPctPerAnnum;

    @Column(name = "first_response_minutes_critical", nullable = false)
    private int firstResponseMinutesCritical;

    @Column(name = "resolve_minutes_critical", nullable = false)
    private int resolveMinutesCritical;

    @Column(name = "first_response_minutes_high", nullable = false)
    private int firstResponseMinutesHigh;

    @Column(name = "resolve_minutes_high", nullable = false)
    private int resolveMinutesHigh;

    @Column(name = "first_response_minutes_medium", nullable = false)
    private int firstResponseMinutesMedium;

    @Column(name = "resolve_minutes_medium", nullable = false)
    private int resolveMinutesMedium;

    @Column(name = "first_response_minutes_low", nullable = false)
    private int firstResponseMinutesLow;

    @Column(name = "resolve_minutes_low", nullable = false)
    private int resolveMinutesLow;

    @Column(name = "ai_confidence_threshold", nullable = false, precision = 3, scale = 2)
    private BigDecimal aiConfidenceThreshold;

    protected SocietySettings() {
    }

    public BigDecimal getRatePerSqft() {
        return ratePerSqft;
    }

    public int getDueDay() {
        return dueDay;
    }

    public BigDecimal getLateInterestPctPerAnnum() {
        return lateInterestPctPerAnnum;
    }

    public int getFirstResponseMinutesCritical() {
        return firstResponseMinutesCritical;
    }

    public int getResolveMinutesCritical() {
        return resolveMinutesCritical;
    }

    public int getFirstResponseMinutesHigh() {
        return firstResponseMinutesHigh;
    }

    public int getResolveMinutesHigh() {
        return resolveMinutesHigh;
    }

    public int getFirstResponseMinutesMedium() {
        return firstResponseMinutesMedium;
    }

    public int getResolveMinutesMedium() {
        return resolveMinutesMedium;
    }

    public int getFirstResponseMinutesLow() {
        return firstResponseMinutesLow;
    }

    public int getResolveMinutesLow() {
        return resolveMinutesLow;
    }

    public BigDecimal getAiConfidenceThreshold() {
        return aiConfidenceThreshold;
    }
}
