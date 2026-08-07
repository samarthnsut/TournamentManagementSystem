package com.acme.tms.result.domain;

import com.acme.tms.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A materialized standings row. Derived data (BR-LE-2): the whole competition's rows are deleted
 * and rewritten on every result confirmation, so this table is a cache that happens to be
 * transactional with the results it summarizes rather than a source of truth.
 */
@Entity
@Table(name = "leaderboard_entry")
public class LeaderboardEntry extends BaseEntity {

    @Column(nullable = false)
    private UUID competitionId;

    @Column(nullable = false)
    private UUID participantId;

    @Column(name = "rank", nullable = false)
    private int rank;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String metrics;

    @Column(nullable = false)
    private Instant computedAt = Instant.now();

    public UUID getCompetitionId() {
        return competitionId;
    }

    public void setCompetitionId(UUID competitionId) {
        this.competitionId = competitionId;
    }

    public UUID getParticipantId() {
        return participantId;
    }

    public void setParticipantId(UUID participantId) {
        this.participantId = participantId;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getMetrics() {
        return metrics;
    }

    public void setMetrics(String metrics) {
        this.metrics = metrics;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
