package com.acme.tms.fixture.domain;

import com.acme.tms.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Binds a participant into a match with the slot they occupy — HOME/AWAY, or LANE_n for a race. */
@Entity
@Table(name = "match_participant")
public class MatchParticipant extends BaseEntity {

    @Column(nullable = false)
    private UUID matchId;

    @Column(nullable = false)
    private UUID participantId;

    @Column(length = 20)
    private String slot;

    private Integer seed;

    public UUID getMatchId() {
        return matchId;
    }

    public void setMatchId(UUID matchId) {
        this.matchId = matchId;
    }

    public UUID getParticipantId() {
        return participantId;
    }

    public void setParticipantId(UUID participantId) {
        this.participantId = participantId;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }
}
