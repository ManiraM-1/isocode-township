package com.isocode.township.flat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "flat")
public class Flat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tower_id", nullable = false)
    private Tower tower;

    @Column(name = "area_sqft", nullable = false)
    private int areaSqft;

    protected Flat() {
    }

    public Flat(String code, Tower tower, int areaSqft) {
        this.code = code;
        this.tower = tower;
        this.areaSqft = areaSqft;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Tower getTower() {
        return tower;
    }

    public int getAreaSqft() {
        return areaSqft;
    }
}
