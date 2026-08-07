package com.acme.tms.result.domain;

import com.acme.tms.common.domain.BaseEntity;
import com.acme.tms.result.strategy.ResultEvaluatorKey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The recorded outcome of a match, one per match.
 *
 * <p>{@code payload} holds both the official's raw submission and the evaluator's reading of it.
 * Storing the evaluation rather than only the raw scores is what lets leaderboards be rebuilt
 * without re-running an evaluator whose rules may since have been reconfigured.
 */
@Entity
@Table(name = "result")
public class Result extends BaseEntity {

    @Column(nullable = false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultEvaluatorKey evaluatorKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    /** Null for a draw, and for events where no single entrant is "the winner". */
    private UUID winnerParticipantId;

    @Column(nullable = false)
    private UUID recordedBy;

    @Column(nullable = false)
    private Instant recordedAt = Instant.now();

    public UUID getMatchId() {
        return matchId;
    }

    public void setMatchId(UUID matchId) {
        this.matchId = matchId;
    }

    public ResultEvaluatorKey getEvaluatorKey() {
        return evaluatorKey;
    }

    public void setEvaluatorKey(ResultEvaluatorKey evaluatorKey) {
        this.evaluatorKey = evaluatorKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public UUID getWinnerParticipantId() {
        return winnerParticipantId;
    }

    public void setWinnerParticipantId(UUID winnerParticipantId) {
        this.winnerParticipantId = winnerParticipantId;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(UUID recordedBy) {
        this.recordedBy = recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
