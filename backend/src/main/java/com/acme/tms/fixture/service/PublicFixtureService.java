package com.acme.tms.fixture.service;

import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.fixture.domain.Fixture;
import com.acme.tms.fixture.dto.MatchResponse;
import com.acme.tms.fixture.dto.PublicFixturesResponse;
import com.acme.tms.fixture.repository.FixtureRepository;
import com.acme.tms.fixture.repository.MatchRepository;
import com.acme.tms.result.dto.ResultSummaryResponse;
import com.acme.tms.tournament.domain.Competition;
import com.acme.tms.tournament.service.PublicAccessService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Backs the anonymous fixtures list on {@code /t/{slug}}. */
@Service
public class PublicFixtureService {

    private final PublicAccessService publicAccessService;
    private final FixtureRepository fixtureRepository;
    private final MatchRepository matchRepository;
    private final MatchAssembler matchAssembler;

    public PublicFixtureService(
        PublicAccessService publicAccessService,
        FixtureRepository fixtureRepository,
        MatchRepository matchRepository,
        MatchAssembler matchAssembler
    ) {
        this.publicAccessService = publicAccessService;
        this.fixtureRepository = fixtureRepository;
        this.matchRepository = matchRepository;
        this.matchAssembler = matchAssembler;
    }

    @Transactional(readOnly = true)
    public PublicFixturesResponse getBySlug(String slug, UUID competitionId) {
        Competition competition = publicAccessService.requirePublicCompetition(slug, competitionId);

        List<Fixture> fixtures = fixtureRepository.findByCompetitionIdOrderByRoundNumberAsc(competitionId);
        if (fixtures.isEmpty()) {
            throw new ResourceNotFoundException("FIXTURE_NOT_FOUND", "This competition has no fixtures yet.");
        }

        // Reusing the organizer assembler keeps the batched reads and one mapping of results;
        // the narrowing to the public shape happens on the way out.
        List<MatchResponse> matches =
            matchAssembler.assemble(matchRepository.findByCompetitionIdOrderByCreatedAtAsc(competitionId));

        Map<UUID, List<MatchResponse>> byFixture = new HashMap<>();
        matches.forEach(match ->
            byFixture.computeIfAbsent(match.fixtureId(), key -> new ArrayList<>()).add(match));

        List<PublicFixturesResponse.PublicRound> rounds = fixtures.stream()
            .sorted(Comparator.comparingInt(Fixture::getRoundNumber))
            .map(fixture -> new PublicFixturesResponse.PublicRound(
                fixture.getRoundNumber(),
                fixture.getRoundName(),
                byFixture.getOrDefault(fixture.getId(), List.of()).stream()
                    .map(this::toPublicMatch)
                    .toList()
            ))
            .toList();

        return new PublicFixturesResponse(
            competition.getId(),
            competition.getName(),
            fixtures.get(0).getGeneratorKey(),
            rounds.size(),
            matches.size(),
            rounds
        );
    }

    private PublicFixturesResponse.PublicMatch toPublicMatch(MatchResponse match) {
        return new PublicFixturesResponse.PublicMatch(
            match.id(),
            match.status(),
            match.scheduledAt(),
            match.participants().stream()
                .map(participant -> new PublicFixturesResponse.PublicMatchParticipant(
                    participant.participantId(),
                    participant.name(),
                    participant.slot()
                ))
                .toList(),
            toPublicResult(match.result())
        );
    }

    private PublicFixturesResponse.PublicResult toPublicResult(ResultSummaryResponse result) {
        if (result == null) {
            return null;
        }

        return new PublicFixturesResponse.PublicResult(
            result.outcome(),
            result.winnerParticipantId(),
            result.participants().stream()
                .map(participant -> new PublicFixturesResponse.PublicOutcome(
                    participant.participantId(),
                    participant.name(),
                    participant.value(),
                    participant.unit(),
                    participant.standing()
                ))
                .toList()
        );
    }
}
