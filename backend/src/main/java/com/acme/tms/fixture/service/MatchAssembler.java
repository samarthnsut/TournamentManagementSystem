package com.acme.tms.fixture.service;

import com.acme.tms.fixture.domain.Match;
import com.acme.tms.fixture.domain.MatchParticipant;
import com.acme.tms.fixture.dto.MatchParticipantResponse;
import com.acme.tms.fixture.dto.MatchResponse;
import com.acme.tms.fixture.repository.FixtureRepository;
import com.acme.tms.fixture.repository.MatchParticipantRepository;
import com.acme.tms.registration.domain.Participant;
import com.acme.tms.registration.repository.ParticipantRepository;
import com.acme.tms.result.domain.Result;
import com.acme.tms.result.dto.ResultSummaryResponse;
import com.acme.tms.result.repository.ResultRepository;
import com.acme.tms.result.service.ResultPayload;
import com.acme.tms.result.service.ResultPayloadCodec;
import com.acme.tms.tournament.repository.CompetitionRepository;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns match rows into the response clients read.
 *
 * <p>It exists to keep the reads batched: rendering a round-robin season is one query for the
 * participants, one for the results and one for the names, whatever the number of matches. Doing it
 * per match inside a mapper is the classic way this page becomes the slowest in the product.
 */
@Component
public class MatchAssembler {

    private final MatchParticipantRepository matchParticipantRepository;
    private final ParticipantRepository participantRepository;
    private final ResultRepository resultRepository;
    private final FixtureRepository fixtureRepository;
    private final CompetitionRepository competitionRepository;
    private final ResultPayloadCodec resultPayloadCodec;

    public MatchAssembler(
        MatchParticipantRepository matchParticipantRepository,
        ParticipantRepository participantRepository,
        ResultRepository resultRepository,
        FixtureRepository fixtureRepository,
        CompetitionRepository competitionRepository,
        ResultPayloadCodec resultPayloadCodec
    ) {
        this.matchParticipantRepository = matchParticipantRepository;
        this.participantRepository = participantRepository;
        this.resultRepository = resultRepository;
        this.fixtureRepository = fixtureRepository;
        this.competitionRepository = competitionRepository;
        this.resultPayloadCodec = resultPayloadCodec;
    }

    public MatchResponse assembleOne(Match match) {
        return assemble(List.of(match)).get(0);
    }

    public List<MatchResponse> assemble(List<Match> matches) {
        if (matches.isEmpty()) {
            return List.of();
        }

        List<UUID> matchIds = matches.stream().map(Match::getId).toList();

        Map<UUID, List<MatchParticipant>> participantsByMatch = new HashMap<>();
        Set<UUID> participantIds = new HashSet<>();
        for (MatchParticipant participant : matchParticipantRepository.findByMatchIdInOrderByCreatedAtAsc(matchIds)) {
            participantsByMatch.computeIfAbsent(participant.getMatchId(), key -> new ArrayList<>()).add(participant);
            participantIds.add(participant.getParticipantId());
        }

        Map<UUID, Result> resultsByMatch = new HashMap<>();
        for (Result result : resultRepository.findByMatchIdIn(matchIds)) {
            resultsByMatch.put(result.getMatchId(), result);
            if (result.getWinnerParticipantId() != null) {
                participantIds.add(result.getWinnerParticipantId());
            }
        }

        Map<UUID, String> names = displayNames(participantIds);
        Map<UUID, Integer> roundsByFixture = roundNumbers(matches);
        Map<UUID, UUID> owningUnits = owningUnits(matches);

        List<MatchResponse> responses = new ArrayList<>(matches.size());
        for (Match match : matches) {
            List<MatchParticipant> lineup =
                participantsByMatch.getOrDefault(match.getId(), List.of());
            lineup = new ArrayList<>(lineup);
            lineup.sort(Comparator.comparing(participant -> slotOrder(participant.getSlot())));

            responses.add(new MatchResponse(
                match.getId(),
                match.getCompetitionId(),
                owningUnits.get(match.getCompetitionId()),
                match.getFixtureId(),
                roundsByFixture.get(match.getFixtureId()),
                match.getStatus(),
                match.getScheduledAt(),
                match.getVenueId(),
                match.getVersion(),
                lineup.stream()
                    .map(participant -> new MatchParticipantResponse(
                        participant.getParticipantId(),
                        names.get(participant.getParticipantId()),
                        participant.getSlot(),
                        participant.getSeed()
                    ))
                    .toList(),
                summarize(resultsByMatch.get(match.getId()), names)
            ));
        }

        return List.copyOf(responses);
    }

    public ResultSummaryResponse summarize(Result result, Map<UUID, String> names) {
        if (result == null) {
            return null;
        }

        ResultPayload payload = resultPayloadCodec.decode(result.getPayload());

        return new ResultSummaryResponse(
            result.getId(),
            result.getEvaluatorKey(),
            payload.raw().outcome(),
            result.getWinnerParticipantId(),
            result.getRecordedAt(),
            payload.evaluation().participants().stream()
                .map(participant -> new ResultSummaryResponse.ParticipantOutcomeResponse(
                    participant.participantId(),
                    names.get(participant.participantId()),
                    participant.value(),
                    participant.unit(),
                    participant.points(),
                    participant.standing()
                ))
                .toList()
        );
    }

    public Map<UUID, String> displayNames(Set<UUID> participantIds) {
        if (participantIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> names = new LinkedHashMap<>();
        participantRepository.findAllById(participantIds)
            .forEach(participant -> names.put(participant.getId(), participant.getDisplayName()));
        return names;
    }

    /** One lookup per competition, however many matches — the batching rule this class exists for. */
    private Map<UUID, UUID> owningUnits(List<Match> matches) {
        Set<UUID> competitionIds = new HashSet<>();
        matches.forEach(match -> competitionIds.add(match.getCompetitionId()));
        if (competitionIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UUID> units = new HashMap<>();
        competitionRepository.findAllById(competitionIds)
            .forEach(competition -> units.put(competition.getId(), competition.getOrganizationUnitId()));
        return units;
    }

    private Map<UUID, Integer> roundNumbers(List<Match> matches) {
        Set<UUID> fixtureIds = new HashSet<>();
        matches.forEach(match -> {
            if (match.getFixtureId() != null) {
                fixtureIds.add(match.getFixtureId());
            }
        });
        if (fixtureIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Integer> rounds = new HashMap<>();
        fixtureRepository.findAllById(fixtureIds)
            .forEach(fixture -> rounds.put(fixture.getId(), fixture.getRoundNumber()));
        return rounds;
    }

    /**
     * Slots are labels, not an ordering, but a lineup that shuffles between reads looks like a bug.
     * HOME leads AWAY, lanes go up numerically, and anything else falls back to its own text.
     */
    private String slotOrder(String slot) {
        if (slot == null) {
            return "zz";
        }
        if (slot.equals("HOME")) {
            return "a";
        }
        if (slot.equals("AWAY")) {
            return "b";
        }
        if (slot.startsWith("LANE_")) {
            String lane = slot.substring("LANE_".length());
            // Zero-padded so LANE_2 sorts before LANE_10.
            return "c" + "0".repeat(Math.max(0, 4 - lane.length())) + lane;
        }
        return "y" + slot;
    }

    /** Convenience for callers that only have a match and want its lineup identities. */
    public List<UUID> participantIdsOf(UUID matchId) {
        List<MatchParticipant> lineup =
            new ArrayList<>(matchParticipantRepository.findByMatchIdOrderByCreatedAtAsc(matchId));
        lineup.sort(Comparator.comparing(participant -> slotOrder(participant.getSlot())));
        return lineup.stream().map(MatchParticipant::getParticipantId).toList();
    }
}
