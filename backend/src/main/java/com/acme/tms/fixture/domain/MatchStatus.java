package com.acme.tms.fixture.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Match lifecycle from 02_DOMAIN_MODEL section 5.12, held as data like the other lifecycles. */
public enum MatchStatus {
    SCHEDULED,
    LIVE,
    COMPLETED,
    WALKOVER,
    CANCELLED,
    POSTPONED;

    private static final Map<MatchStatus, Set<MatchStatus>> ALLOWED = Map.of(
        // Officials routinely record a result without ever flagging a match LIVE — a scoreboard is
        // for spectators, and refusing the result would be the system arguing with reality.
        SCHEDULED, EnumSet.of(LIVE, COMPLETED, WALKOVER, POSTPONED, CANCELLED),
        LIVE, EnumSet.of(COMPLETED, WALKOVER, CANCELLED),
        POSTPONED, EnumSet.of(SCHEDULED, CANCELLED),
        COMPLETED, EnumSet.noneOf(MatchStatus.class),
        WALKOVER, EnumSet.noneOf(MatchStatus.class),
        CANCELLED, EnumSet.noneOf(MatchStatus.class)
    );

    public boolean canTransitionTo(MatchStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** Immutable except through an audited correction flow (BR-M-3). */
    public boolean isFinal() {
        return this == COMPLETED || this == WALKOVER || this == CANCELLED;
    }

    /** Anything past SCHEDULED means the draw is in play and may no longer be rebuilt (BR-F-3). */
    public boolean blocksRegeneration() {
        return this != SCHEDULED && this != POSTPONED;
    }

    /** Only these two contribute to a leaderboard; the rest were never contested. */
    public boolean producesResult() {
        return this == COMPLETED || this == WALKOVER;
    }
}
