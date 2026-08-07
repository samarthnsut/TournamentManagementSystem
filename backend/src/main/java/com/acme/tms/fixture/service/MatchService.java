package com.acme.tms.fixture.service;

import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.fixture.domain.Match;
import com.acme.tms.fixture.domain.MatchStatus;
import com.acme.tms.fixture.dto.MatchResponse;
import com.acme.tms.fixture.dto.ScheduleMatchRequest;
import com.acme.tms.fixture.dto.ScheduledMatchResponse;
import com.acme.tms.fixture.repository.MatchRepository;
import com.acme.tms.tournament.domain.Competition;
import com.acme.tms.tournament.domain.Tournament;
import com.acme.tms.tournament.repository.VenueRepository;
import com.acme.tms.tournament.service.CompetitionConfigResolver;
import com.acme.tms.tournament.service.CompetitionService;
import com.acme.tms.tournament.service.TournamentService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class MatchService {

    /** Used to size the clash window when the sport's rules do not state a match duration. */
    private static final int DEFAULT_MATCH_MINUTES = 60;

    private final MatchRepository matchRepository;
    private final VenueRepository venueRepository;
    private final CompetitionService competitionService;
    private final TournamentService tournamentService;
    private final CompetitionConfigResolver competitionConfigResolver;
    private final MatchAssembler matchAssembler;

    public MatchService(
        MatchRepository matchRepository,
        VenueRepository venueRepository,
        CompetitionService competitionService,
        TournamentService tournamentService,
        CompetitionConfigResolver competitionConfigResolver,
        MatchAssembler matchAssembler
    ) {
        this.matchRepository = matchRepository;
        this.venueRepository = venueRepository;
        this.competitionService = competitionService;
        this.tournamentService = tournamentService;
        this.competitionConfigResolver = competitionConfigResolver;
        this.matchAssembler = matchAssembler;
    }

    @Transactional
    public ScheduledMatchResponse schedule(UUID matchId, ScheduleMatchRequest request) {
        Match match = require(matchId);

        if (match.getStatus().isFinal()) {
            throw new ConflictException(
                "MATCH_FINALIZED",
                "A " + match.getStatus() + " match can no longer be rescheduled."
            );
        }

        Competition competition = competitionService.require(match.getCompetitionId());
        Tournament tournament = tournamentService.require(competition.getTournamentId());
        requireWithinTournamentDates(tournament, request.scheduledAt());

        if (request.venueId() != null && venueRepository.findByIdAndDeletedAtIsNull(request.venueId()).isEmpty()) {
            throw new ResourceNotFoundException("VENUE_NOT_FOUND", "Venue not found.");
        }

        match.setScheduledAt(request.scheduledAt());
        match.setVenueId(request.venueId());
        // Giving a postponed match a new slot is what puts it back on the calendar.
        if (match.getStatus() == MatchStatus.POSTPONED) {
            match.setStatus(MatchStatus.SCHEDULED);
        }
        matchRepository.flush();

        return new ScheduledMatchResponse(
            matchAssembler.assembleOne(match),
            venueClashWarnings(match, competition)
        );
    }

    @Transactional
    public MatchResponse transition(UUID matchId, MatchStatus target) {
        Match match = require(matchId);

        if (!match.getStatus().canTransitionTo(target)) {
            throw new ConflictException(
                "INVALID_STATE_TRANSITION",
                "Cannot transition match from " + match.getStatus() + " to " + target + "."
            );
        }
        if (target.producesResult()) {
            // A result is what makes a match COMPLETED or WALKOVER (BR-M-2); there is no way to
            // declare either without recording one.
            throw new ValidationException(
                "RESULT_REQUIRED",
                "Record a result to move a match to " + target + "."
            );
        }

        match.setStatus(target);
        matchRepository.flush();
        return matchAssembler.assembleOne(match);
    }

    @Transactional(readOnly = true)
    public MatchResponse get(UUID matchId) {
        return matchAssembler.assembleOne(require(matchId));
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> listForCompetition(UUID competitionId) {
        competitionService.require(competitionId);
        return matchAssembler.assemble(matchRepository.findByCompetitionIdOrderByCreatedAtAsc(competitionId));
    }

    public Match require(UUID matchId) {
        return matchRepository.findById(matchId)
            .orElseThrow(() -> new ResourceNotFoundException("MATCH_NOT_FOUND", "Match not found."));
    }

    private void requireWithinTournamentDates(Tournament tournament, Instant scheduledAt) {
        LocalDate day = scheduledAt.atZone(ZoneOffset.UTC).toLocalDate();

        if (tournament.getStartDate() != null && day.isBefore(tournament.getStartDate())) {
            throw new ValidationException(
                "SCHEDULE_OUTSIDE_TOURNAMENT",
                "The tournament does not start until " + tournament.getStartDate() + "."
            );
        }
        if (tournament.getEndDate() != null && day.isAfter(tournament.getEndDate())) {
            throw new ValidationException(
                "SCHEDULE_OUTSIDE_TOURNAMENT",
                "The tournament ends on " + tournament.getEndDate() + "."
            );
        }
    }

    /**
     * BR-M-4 is a warning, not a constraint: organizers do legitimately run two things on one
     * ground, and a hard rejection would only teach them to schedule around the software. The
     * window is the configured match duration on either side, since a clash is an overlap rather
     * than an identical start time.
     */
    private List<String> venueClashWarnings(Match match, Competition competition) {
        if (match.getVenueId() == null || match.getScheduledAt() == null) {
            return List.of();
        }

        int minutes = Math.max(
            1,
            competitionConfigResolver.resolve(competition).rules()
                .getInt("matchDurationMinutes", DEFAULT_MATCH_MINUTES)
        );
        Duration window = Duration.ofMinutes(minutes);

        List<Match> overlapping = matchRepository.findByVenueIdAndScheduledAtBetweenAndStatusNotIn(
            match.getVenueId(),
            match.getScheduledAt().minus(window),
            match.getScheduledAt().plus(window),
            EnumSet.of(MatchStatus.CANCELLED)
        );

        long clashes = overlapping.stream()
            .filter(other -> !other.getId().equals(match.getId()))
            .count();

        return clashes == 0
            ? List.of()
            : List.of("VENUE_SLOT_CONFLICT: " + clashes + " other match(es) are booked at this venue within "
                + minutes + " minutes of this slot.");
    }
}
