package com.acme.tms.tournament.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transition tables are data, so they can be asserted exhaustively here rather than discovered
 * one bug at a time through the API.
 */
class LifecycleTransitionTest {

    /** The full legal set from 02_DOMAIN_MODEL section 5.3, written out independently. */
    private static final Map<TournamentStatus, Set<TournamentStatus>> EXPECTED_TOURNAMENT = Map.of(
        TournamentStatus.DRAFT, Set.of(TournamentStatus.PUBLISHED, TournamentStatus.CANCELLED),
        TournamentStatus.PUBLISHED, Set.of(TournamentStatus.REGISTRATION_OPEN, TournamentStatus.CANCELLED),
        TournamentStatus.REGISTRATION_OPEN, Set.of(TournamentStatus.REGISTRATION_CLOSED, TournamentStatus.CANCELLED),
        TournamentStatus.REGISTRATION_CLOSED, Set.of(
            TournamentStatus.REGISTRATION_OPEN, TournamentStatus.IN_PROGRESS, TournamentStatus.CANCELLED),
        TournamentStatus.IN_PROGRESS, Set.of(TournamentStatus.COMPLETED, TournamentStatus.CANCELLED),
        TournamentStatus.COMPLETED, Set.of(TournamentStatus.ARCHIVED),
        TournamentStatus.CANCELLED, Set.of(TournamentStatus.ARCHIVED),
        TournamentStatus.ARCHIVED, Set.of()
    );

    private static final Map<CompetitionStatus, Set<CompetitionStatus>> EXPECTED_COMPETITION = Map.of(
        CompetitionStatus.DRAFT, Set.of(CompetitionStatus.OPEN, CompetitionStatus.CANCELLED),
        CompetitionStatus.OPEN, Set.of(CompetitionStatus.CLOSED, CompetitionStatus.CANCELLED),
        CompetitionStatus.CLOSED, Set.of(
            CompetitionStatus.OPEN, CompetitionStatus.IN_PROGRESS, CompetitionStatus.CANCELLED),
        CompetitionStatus.IN_PROGRESS, Set.of(CompetitionStatus.COMPLETED, CompetitionStatus.CANCELLED),
        CompetitionStatus.COMPLETED, Set.of(),
        CompetitionStatus.CANCELLED, Set.of()
    );

    @ParameterizedTest
    @EnumSource(TournamentStatus.class)
    void tournamentAllowsExactlyTheDocumentedTransitions(TournamentStatus from) {
        for (TournamentStatus to : TournamentStatus.values()) {
            assertThat(from.canTransitionTo(to))
                .as("%s -> %s", from, to)
                .isEqualTo(EXPECTED_TOURNAMENT.get(from).contains(to));
        }
    }

    @ParameterizedTest
    @EnumSource(CompetitionStatus.class)
    void competitionAllowsExactlyTheDocumentedTransitions(CompetitionStatus from) {
        for (CompetitionStatus to : CompetitionStatus.values()) {
            assertThat(from.canTransitionTo(to))
                .as("%s -> %s", from, to)
                .isEqualTo(EXPECTED_COMPETITION.get(from).contains(to));
        }
    }

    @Test
    void noTournamentStateCanTransitionToItself() {
        for (TournamentStatus status : TournamentStatus.values()) {
            assertThat(status.canTransitionTo(status)).as("%s -> itself", status).isFalse();
        }
    }

    @Test
    void onlyDraftHidesATournamentFromThePublic() {
        for (TournamentStatus status : TournamentStatus.values()) {
            assertThat(status.isPubliclyVisible())
                .as("%s public visibility", status)
                .isEqualTo(status != TournamentStatus.DRAFT);
        }
    }

    @Test
    void completedAndCancelledAreTheTerminalCompetitionStates() {
        for (CompetitionStatus status : CompetitionStatus.values()) {
            assertThat(status.isTerminal())
                .as("%s terminal", status)
                .isEqualTo(status == CompetitionStatus.COMPLETED || status == CompetitionStatus.CANCELLED);
        }
    }
}
