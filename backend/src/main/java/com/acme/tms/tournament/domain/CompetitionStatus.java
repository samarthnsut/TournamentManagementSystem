package com.acme.tms.tournament.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Competition lifecycle from 02_DOMAIN_MODEL section 5.4, held as data for the same reason. */
public enum CompetitionStatus {
    DRAFT,
    OPEN,
    CLOSED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    private static final Map<CompetitionStatus, Set<CompetitionStatus>> ALLOWED = Map.of(
        DRAFT, EnumSet.of(OPEN, CANCELLED),
        OPEN, EnumSet.of(CLOSED, CANCELLED),
        CLOSED, EnumSet.of(OPEN, IN_PROGRESS, CANCELLED),
        IN_PROGRESS, EnumSet.of(COMPLETED, CANCELLED),
        COMPLETED, EnumSet.noneOf(CompetitionStatus.class),
        CANCELLED, EnumSet.noneOf(CompetitionStatus.class)
    );

    public boolean canTransitionTo(CompetitionStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** A tournament may only complete once every competition has reached one of these (BR-T-3). */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
