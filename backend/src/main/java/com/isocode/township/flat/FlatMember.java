package com.isocode.township.flat;

import com.isocode.township.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "flat_member")
public class FlatMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flat_id", nullable = false)
    private Flat flat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MemberRelation relation;

    @Column(nullable = false)
    private boolean active;

    protected FlatMember() {
    }

    public FlatMember(Flat flat, AppUser user, MemberRelation relation) {
        this.flat = flat;
        this.user = user;
        this.relation = relation;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public Flat getFlat() {
        return flat;
    }

    public AppUser getUser() {
        return user;
    }

    public MemberRelation getRelation() {
        return relation;
    }

    public boolean isActive() {
        return active;
    }
}
