package com.acme.tms.result.service;

import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.fixture.domain.Match;
import com.acme.tms.fixture.repository.MatchRepository;
import com.acme.tms.fixture.service.MatchAssembler;
import com.acme.tms.registration.domain.Registration;
import com.acme.tms.registration.domain.RegistrationStatus;
import com.acme.tms.registration.repository.RegistrationRepository;
import com.acme.tms.result.domain.LeaderboardEntry;
import com.acme.tms.result.domain.Result;
import com.acme.tms.result.dto.LeaderboardResponse;
import com.acme.tms.result.repository.LeaderboardEntryRepository;
import com.acme.tms.result.repository.ResultRepository;
import com.acme.tms.result.strategy.CompetitionResults;
import com.acme.tms.result.strategy.LeaderboardRow;
import com.acme.tms.result.strategy.LeaderboardStrategy;
import com.acme.tms.result.strategy.LeaderboardStrategyFactory;
import com.acme.tms.tournament.domain.Competition;
import com.acme.tms.tournament.domain.CompetitionStatus;
import com.acme.tms.tournament.service.CompetitionConfigResolver;
import com.acme.tms.tournament.service.PublicAccessService;
import com.acme.tms.tournament.service.CompetitionService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps a competition's standings in step with its results.
 *
 * <p>The board is recomputed from scratch on every confirmation and the previous rows are replaced
 * wholesale. That is more work than patching two rows, but it makes the stored board a pure
 * function of the stored results (BR-LE-2) — there is no incremental state to drift, and a bug in
 * a tiebreaker is fixed by deploying and recording anything, not by a repair script.
 *
 * <p>Doc 03 puts a Redis read-through cache in front of this. The materialized table is the V1
 * cache (04 section 12, decision 3): it is written in the same transaction as the result, so it can
 * never serve a standing that disagrees with the match it came from.
 */
@Service
public class LeaderboardService {

    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final MatchRepository matchRepository;
    private final ResultRepository resultRepository;
    private final RegistrationRepository registrationRepository;
    private final CompetitionService competitionService;
    private final CompetitionConfigResolver competitionConfigResolver;
    private final LeaderboardStrategyFactory leaderboardStrategyFactory;
    private final ResultPayloadCodec resultPayloadCodec;
    private final MatchAssembler matchAssembler;
    private final PublicAccessService publicAccessService;
    private final ObjectMapper objectMapper;

    public LeaderboardService(
        LeaderboardEntryRepository leaderboardEntryRepository,
        MatchRepository matchRepository,
        ResultRepository resultRepository,
        RegistrationRepository registrationRepository,
        CompetitionService competitionService,
        CompetitionConfigResolver competitionConfigResolver,
        LeaderboardStrategyFactory leaderboardStrategyFactory,
        ResultPayloadCodec resultPayloadCodec,
        MatchAssembler matchAssembler,
        PublicAccessService publicAccessService,
        ObjectMapper objectMapper
    ) {
        this.leaderboardEntryRepository = leaderboardEntryRepository;
        this.matchRepository = matchRepository;
        this.resultRepository = resultRepository;
        this.registrationRepository = registrationRepository;
        this.competitionService = competitionService;
        this.competitionConfigResolver = competitionConfigResolver;
        this.leaderboardStrategyFactory = leaderboardStrategyFactory;
        this.resultPayloadCodec = resultPayloadCodec;
        this.matchAssembler = matchAssembler;
        this.publicAccessService = publicAccessService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recompute(Competition competition) {
        // BR-LE-3: a completed competition's board is the record of what happened.
        if (competition.getStatus() == CompetitionStatus.COMPLETED) {
            return;
        }

        CompetitionConfigResolver.ResolvedConfig config = competitionConfigResolver.resolve(competition);
        LeaderboardStrategy strategy = leaderboardStrategyFactory.get(config.leaderboardStrategy());

        List<LeaderboardRow> rows = strategy.rank(collectResults(competition.getId()), config.rules());

        leaderboardEntryRepository.deleteByCompetitionId(competition.getId());
        leaderboardEntryRepository.flush();

        Instant computedAt = Instant.now();
        List<LeaderboardEntry> entries = new ArrayList<>(rows.size());
        for (LeaderboardRow row : rows) {
            LeaderboardEntry entry = new LeaderboardEntry();
            entry.setCompetitionId(competition.getId());
            entry.setParticipantId(row.participantId());
            entry.setRank(row.rank());
            entry.setMetrics(writeMetrics(row.metrics()));
            entry.setComputedAt(computedAt);
            entries.add(entry);
        }
        leaderboardEntryRepository.saveAll(entries);
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse get(UUID competitionId) {
        return read(competitionService.require(competitionId));
    }

    /**
     * The anonymous board behind {@code /t/{slug}}. Identical rows and shape — the only difference
     * is that the slug gate decides whether the competition may be read at all, rather than a
     * permission. Sharing {@link #read} is what stops the public board drifting from the real one.
     */
    @Transactional(readOnly = true)
    public LeaderboardResponse getBySlug(String slug, UUID competitionId) {
        return read(publicAccessService.requirePublicCompetition(slug, competitionId));
    }

    private LeaderboardResponse read(Competition competition) {
        UUID competitionId = competition.getId();
        List<LeaderboardEntry> entries = leaderboardEntryRepository.findByCompetitionIdOrderByRankAsc(competitionId);

        if (entries.isEmpty()) {
            // A 409 rather than an empty 200, so a client can tell "not started" from "everyone is
            // on nil points" (08 section 13.1).
            throw new ConflictException(
                "LEADERBOARD_NOT_AVAILABLE",
                "No results have been recorded for this competition yet."
            );
        }

        Set<UUID> participantIds = new LinkedHashSet<>();
        entries.forEach(entry -> participantIds.add(entry.getParticipantId()));
        Map<UUID, String> names = matchAssembler.displayNames(participantIds);

        return new LeaderboardResponse(
            competitionId,
            competitionConfigResolver.resolve(competition).leaderboardStrategy(),
            entries.get(0).getComputedAt(),
            competition.getStatus() == CompetitionStatus.COMPLETED,
            entries.stream()
                .map(entry -> new LeaderboardResponse.Entry(
                    entry.getRank(),
                    entry.getParticipantId(),
                    names.get(entry.getParticipantId()),
                    readMetrics(entry.getMetrics())
                ))
                .toList()
        );
    }

    private CompetitionResults collectResults(UUID competitionId) {
        List<Registration> approved = registrationRepository
            .findByCompetitionIdAndStatusAndDeletedAtIsNullOrderBySubmittedAtAsc(
                competitionId, RegistrationStatus.APPROVED);

        Set<UUID> roster = new LinkedHashSet<>();
        approved.forEach(registration -> roster.add(registration.getParticipantId()));

        List<Match> matches = matchRepository.findByCompetitionIdOrderByCreatedAtAsc(competitionId).stream()
            .filter(match -> match.getStatus().producesResult())
            .toList();

        Map<UUID, Result> resultsByMatch = new HashMap<>();
        if (!matches.isEmpty()) {
            resultRepository.findByMatchIdIn(matches.stream().map(Match::getId).toList())
                .forEach(result -> resultsByMatch.put(result.getMatchId(), result));
        }

        List<CompetitionResults.MatchResultView> views = new ArrayList<>(matches.size());
        Set<UUID> seen = new LinkedHashSet<>(roster);
        for (Match match : matches) {
            Result result = resultsByMatch.get(match.getId());
            if (result == null) {
                // A finished match with no result row would be a bug elsewhere; ranking around it
                // beats failing the whole board.
                continue;
            }
            ResultPayload payload = resultPayloadCodec.decode(result.getPayload());
            payload.evaluation().participants()
                .forEach(participant -> seen.add(participant.participantId()));
            views.add(new CompetitionResults.MatchResultView(
                match.getId(),
                payload.raw().outcome(),
                payload.evaluation().participants()
            ));
        }

        // Anyone who played but is no longer on the approved roster still has to appear, or the
        // table would not add up against the matches it summarizes.
        return new CompetitionResults(List.copyOf(seen), List.copyOf(views));
    }

    private String writeMetrics(Map<String, Object> metrics) {
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Leaderboard metrics could not be serialized", exception);
        }
    }

    private Map<String, Object> readMetrics(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored leaderboard metrics are unreadable", exception);
        }
    }
}
