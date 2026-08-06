package com.acme.tms.tournament.domain;

import com.acme.tms.common.domain.RegistrationApprovalPolicy;
import com.acme.tms.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tournament")
public class Tournament extends SoftDeletableEntity {

    @Column(nullable = false)
    private UUID organizationUnitId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 120)
    private String slug;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TournamentStatus status = TournamentStatus.DRAFT;

    private Instant publishedAt;

    /** Null means inherit the owning organization unit's policy. */
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_approval_policy", length = 30)
    private RegistrationApprovalPolicy registrationApprovalPolicy;

    /** The slug is part of a public URL, so it may only change while nobody could have seen it. */
    public boolean isSlugMutable() {
        return status == TournamentStatus.DRAFT;
    }

    public UUID getOrganizationUnitId() {
        return organizationUnitId;
    }

    public void setOrganizationUnitId(UUID organizationUnitId) {
        this.organizationUnitId = organizationUnitId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public TournamentStatus getStatus() {
        return status;
    }

    public void setStatus(TournamentStatus status) {
        this.status = status;
    }

    public RegistrationApprovalPolicy getRegistrationApprovalPolicy() {
        return registrationApprovalPolicy;
    }

    public void setRegistrationApprovalPolicy(RegistrationApprovalPolicy registrationApprovalPolicy) {
        this.registrationApprovalPolicy = registrationApprovalPolicy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
