package com.acme.tms.tournament.domain;

import com.acme.tms.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "competition")
public class Competition extends SoftDeletableEntity {

    @Column(nullable = false)
    private UUID tournamentId;

    /** Denormalized from the tournament so scope checks never need a join (BR-C-6). */
    @Column(nullable = false)
    private UUID organizationUnitId;

    @Column(nullable = false)
    private UUID sportId;

    @Column(nullable = false)
    private UUID sportConfigurationId;

    @Column(nullable = false, length = 200)
    private String name;

    private Integer maxRegistrations;

    private Instant registrationOpenAt;

    private Instant registrationCloseAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompetitionStatus status = CompetitionStatus.DRAFT;

    public UUID getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(UUID tournamentId) {
        this.tournamentId = tournamentId;
    }

    public UUID getOrganizationUnitId() {
        return organizationUnitId;
    }

    public void setOrganizationUnitId(UUID organizationUnitId) {
        this.organizationUnitId = organizationUnitId;
    }

    public UUID getSportId() {
        return sportId;
    }

    public void setSportId(UUID sportId) {
        this.sportId = sportId;
    }

    public UUID getSportConfigurationId() {
        return sportConfigurationId;
    }

    public void setSportConfigurationId(UUID sportConfigurationId) {
        this.sportConfigurationId = sportConfigurationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMaxRegistrations() {
        return maxRegistrations;
    }

    public void setMaxRegistrations(Integer maxRegistrations) {
        this.maxRegistrations = maxRegistrations;
    }

    public Instant getRegistrationOpenAt() {
        return registrationOpenAt;
    }

    public void setRegistrationOpenAt(Instant registrationOpenAt) {
        this.registrationOpenAt = registrationOpenAt;
    }

    public Instant getRegistrationCloseAt() {
        return registrationCloseAt;
    }

    public void setRegistrationCloseAt(Instant registrationCloseAt) {
        this.registrationCloseAt = registrationCloseAt;
    }

    public CompetitionStatus getStatus() {
        return status;
    }

    public void setStatus(CompetitionStatus status) {
        this.status = status;
    }
}
