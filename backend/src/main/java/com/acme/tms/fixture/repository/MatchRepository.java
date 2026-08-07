package com.acme.tms.fixture.repository;

import com.acme.tms.fixture.domain.Match;
import com.acme.tms.fixture.domain.MatchStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    List<Match> findByCompetitionIdOrderByCreatedAtAsc(UUID competitionId);

    List<Match> findByCompetitionIdAndStatusInOrderByCreatedAtAsc(
        UUID competitionId, Collection<MatchStatus> statuses);

    List<Match> findByFixtureIdOrderByCreatedAtAsc(UUID fixtureId);

    boolean existsByCompetitionIdAndStatusIn(UUID competitionId, Collection<MatchStatus> statuses);

    /** Backs the BR-M-4 double-booking warning; an unscheduled match cannot clash with anything. */
    List<Match> findByVenueIdAndScheduledAtBetweenAndStatusNotIn(
        UUID venueId, Instant from, Instant to, Collection<MatchStatus> excludedStatuses);

    void deleteByCompetitionId(UUID competitionId);
}
