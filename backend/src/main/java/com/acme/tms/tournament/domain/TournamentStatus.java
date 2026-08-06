package com.acme.tms.tournament.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Tournament lifecycle from 02_DOMAIN_MODEL section 5.3. The legal transitions are held as data
 * rather than as branching in the service, so the whole table can be asserted in one test and a
 * new state cannot quietly bypass a check.
 */
public enum TournamentStatus {
    DRAFT,
    PUBLISHED,
    REGISTRATION_OPEN,
    REGISTRATION_CLOSED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    ARCHIVED;

    private static final Map<TournamentStatus, Set<TournamentStatus>> ALLOWED = Map.of(
        DRAFT, EnumSet.of(PUBLISHED, CANCELLED),
        PUBLISHED, EnumSet.of(REGISTRATION_OPEN, CANCELLED),
        REGISTRATION_OPEN, EnumSet.of(REGISTRATION_CLOSED, CANCELLED),
        REGISTRATION_CLOSED, EnumSet.of(REGISTRATION_OPEN, IN_PROGRESS, CANCELLED),
        IN_PROGRESS, EnumSet.of(COMPLETED, CANCELLED),
        COMPLETED, EnumSet.of(ARCHIVED),
        CANCELLED, EnumSet.of(ARCHIVED),
        ARCHIVED, EnumSet.noneOf(TournamentStatus.class)
    );

    public boolean canTransitionTo(TournamentStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** Past this point the tournament is history: no edits, no further transitions except archive. */
    public boolean isTerminal() {
        return this == ARCHIVED;
    }

    /** Anonymous visitors may only ever see a tournament that has been published. */
    public boolean isPubliclyVisible() {
        return this != DRAFT;
    }
}
