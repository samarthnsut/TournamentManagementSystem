package com.acme.tms.fixture.domain;

import com.acme.tms.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match")
public class Match extends BaseEntity {

    @Column(nullable = false)
    private UUID competitionId;

    /** Null for a match an official added outside a generated round. */
    private UUID fixtureId;

    private UUID venueId;

    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.SCHEDULED;

    /**
     * Two officials at the same pitch-side table will both submit; the second one's stale version
     * is what turns a silent overwrite into a 409 they can act on.
     */
    @Version
    @Column(nullable = false)
    private int version;

    public UUID getCompetitionId() {
        return competitionId;
    }

    public void setCompetitionId(UUID competitionId) {
        this.competitionId = competitionId;
    }

    public UUID getFixtureId() {
        return fixtureId;
    }

    public void setFixtureId(UUID fixtureId) {
        this.fixtureId = fixtureId;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public void setVenueId(UUID venueId) {
        this.venueId = venueId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public int getVersion() {
        return version;
    }
}
