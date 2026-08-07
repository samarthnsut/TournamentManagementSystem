package com.acme.tms.fixture.domain;

import com.acme.tms.common.domain.BaseEntity;
import com.acme.tms.fixture.strategy.FixtureGeneratorKey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One generated round of a competition. Not soft-deletable: regeneration discards the draw
 * outright, and a half-visible previous draw would be worse than none.
 */
@Entity
@Table(name = "fixture")
public class Fixture extends BaseEntity {

    @Column(nullable = false)
    private UUID competitionId;

    @Column(nullable = false)
    private int roundNumber;

    @Column(length = 100)
    private String roundName;

    /** Recorded per round so a fixture set stays readable after its configuration changes. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FixtureGeneratorKey generatorKey;

    @Column(nullable = false)
    private Instant generatedAt = Instant.now();

    public UUID getCompetitionId() {
        return competitionId;
    }

    public void setCompetitionId(UUID competitionId) {
        this.competitionId = competitionId;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public String getRoundName() {
        return roundName;
    }

    public void setRoundName(String roundName) {
        this.roundName = roundName;
    }

    public FixtureGeneratorKey getGeneratorKey() {
        return generatorKey;
    }

    public void setGeneratorKey(FixtureGeneratorKey generatorKey) {
        this.generatorKey = generatorKey;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }
}
