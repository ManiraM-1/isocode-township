package com.isocode.township.auth;

import com.isocode.township.complaint.ComplaintCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 16)
    private String phone;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(name = "is_duty_manager", nullable = false)
    private boolean dutyManager;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] skills;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    private AppUser(String phone, String fullName, Role role, boolean dutyManager,
                    Collection<ComplaintCategory> skills, Instant createdAt) {
        this.phone = phone;
        this.fullName = fullName;
        this.role = role;
        this.dutyManager = dutyManager;
        this.skills = skills.stream().map(Enum::name).sorted().toArray(String[]::new);
        this.active = true;
        this.createdAt = createdAt;
    }

    public static AppUser resident(String phone, String fullName, Instant createdAt) {
        return new AppUser(phone, fullName, Role.RESIDENT, false, Set.of(), createdAt);
    }

    public static AppUser residentWithoutLogin(String fullName, Instant createdAt) {
        return new AppUser(null, fullName, Role.RESIDENT, false, Set.of(), createdAt);
    }

    public static AppUser staff(String phone, String fullName, Collection<ComplaintCategory> skills,
                                boolean dutyManager, Instant createdAt) {
        return new AppUser(phone, fullName, Role.HELPDESK, dutyManager, skills, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Optional<String> getPhone() {
        return Optional.ofNullable(phone);
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isDutyManager() {
        return dutyManager;
    }

    public Set<ComplaintCategory> getSkills() {
        Set<ComplaintCategory> categories = EnumSet.noneOf(ComplaintCategory.class);
        Arrays.stream(skills).map(ComplaintCategory::valueOf).forEach(categories::add);
        return categories;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
