package com.acme.tms.fixture.service;

import com.acme.tms.common.audit.Audited;
import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.fixture.domain.Fixture;
import com.acme.tms.fixture.domain.Match;
import com.acme.tms.fixture.domain.MatchParticipant;
import com.acme.tms.fixture.domain.MatchStatus;
import com.acme.tms.fixture.dto.FixtureSetResponse;
import com.acme.tms.fixture.dto.GenerateFixturesRequest;
import com.acme.tms.fixture.dto.MatchResponse;
import com.acme.tms.fixture.dto.RegenerateFixturesRequest;
import com.acme.tms.fixture.repository.FixtureRepository;
import com.acme.tms.fixture.repository.MatchRepository;
import com.acme.tms.fixture.repository.MatchParticipantRepository;
import com.acme.tms.fixture.strategy.FixtureGenerationContext;
import com.acme.tms.fixture.strategy.FixtureGenerator;
import com.acme.tms.fixture.strategy.FixtureGeneratorFactory;
import com.acme.tms.fixture.strategy.FixturePlan;
import com.acme.tms.fixture.strategy.SeededParticipant;
import com.acme.tms.registration.domain.Registration;
import com.acme.tms.registration.domain.RegistrationStatus;
import com.acme.tms.registration.repository.ParticipantRepository;
import com.acme.tms.registration.repository.RegistrationRepository;
import com.acme.tms.result.repository.LeaderboardEntryRepository;
import com.acme.tms.result.repository.ResultRepository;
import com.acme.tms.tournament.domain.Competition;
import com.acme.tms.tournament.domain.CompetitionStatus;
import com.acme.tms.tournament.service.CompetitionConfigResolver;
import com.acme.tms.tournament.service.CompetitionService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Draws a competition's fixtures and owns everything about that draw's lifetime.
 *
 * <p>The pairing itself belongs to a {@link FixtureGenerator}; what lives here is the part that
 * genuinely needs a database — deciding the competition is ready, resolving who the entrants are,
 * persisting the plan, and refusing to rebuild a draw that people have already played.
 */
@Service
public class FixtureService {

    /** Statuses a draw may be made in: entries settled, competition not yet finished. */
    private static final java.util.EnumSet<CompetitionStatus> DRAWABLE =
        java.util.EnumSet.of(CompetitionStatus.CLOSED, CompetitionStatus.IN_PROGRESS);

    private final FixtureRepository fixtureRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final ResultRepository resultRepository;
    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final RegistrationRepository registrationRepository;
    private final ParticipantRepository participantRepository;
    private final CompetitionService competitionService;
    private final CompetitionConfigResolver competitionConfigResolver;
    private final FixtureGeneratorFactory fixtureGeneratorFactory;
    private final MatchAssembler matchAssembler;

    public FixtureService(
        FixtureRepository fixtureRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        ResultRepository resultRepository,
        LeaderboardEntryRepository leaderboardEntryRepository,
        RegistrationRepository registrationRepository,
        ParticipantRepository participantRepository,
        CompetitionService competitionService,
        CompetitionConfigResolver competitionConfigResolver,
        FixtureGeneratorFactory fixtureGeneratorFactory,
        MatchAssembler matchAssembler
    ) {
        this.fixtureRepository = fixtureRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.resultRepository = resultRepository;
        this.leaderboardEntryRepository = leaderboardEntryRepository;
        this.registrationRepository = registrationRepository;
        this.participantRepository = participantRepository;
        this.competitionService = competitionService;
        this.competitionConfigResolver = competitionConfigResolver;
        this.fixtureGeneratorFactory = fixtureGeneratorFactory;
        this.matchAssembler = matchAssembler;
    }

    @Transactional
    @Audited(value = "fixture:generate", entityType = "Competition", entityIdParam = "competitionId")
    public FixtureSetResponse generate(UUID competitionId, GenerateFixturesRequest request) {
        Competition competition = competitionService.require(competitionId);

        // BR-F-2: entries have to be settled before anyone can be drawn against anyone. A
        // competition already under way is allowed too — a draw made late is still a valid draw,
        // and refusing it would strand any competition whose organizer pressed Start first.
        if (!DRAWABLE.contains(competition.getStatus())) {
            throw new ConflictException(
                "COMPETITION_NOT_CLOSED",
                competition.getStatus() == CompetitionStatus.OPEN
                    ? "Close entries before generating the draw — the field has to be settled first."
                    : "A " + competition.getStatus().name().toLowerCase().replace('_', ' ')
                        + " competition can no longer be drawn."
            );
        }

        if (fixtureRepository.existsByCompetitionId(competitionId)) {
            throw new ConflictException(
                "FIXTURE_ALREADY_EXISTS",
                "This competition already has fixtures; use regenerate to replace them."
            );
        }

        FixtureSetResponse drawn = draw(competition, request);

        // BR-F-2 again: making the draw is what starts a competition. Leaving that to a separate
        // button is what let organizers close entries, complete the competition, and only then
        // discover the draw could no longer be made.
        if (competition.getStatus() == CompetitionStatus.CLOSED) {
            competition.setStatus(CompetitionStatus.IN_PROGRESS);
        }

        return drawn;
    }

    @Transactional
    @Audited(value = "fixture:regenerate", entityType = "Competition", entityIdParam = "competitionId")
    public FixtureSetResponse regenerate(UUID competitionId, RegenerateFixturesRequest request) {
        Competition competition = competitionService.require(competitionId);

        if (!fixtureRepository.existsByCompetitionId(competitionId)) {
            throw new ResourceNotFoundException(
                "FIXTURE_NOT_FOUND",
                "There is no draw to regenerate for this competition."
            );
        }

        // BR-F-3: once a match has been played, the draw is history and rebuilding it would
        // orphan a result that people watched happen.
        List<Match> inPlay = matchRepository.findByCompetitionIdOrderByCreatedAtAsc(competitionId).stream()
            .filter(match -> match.getStatus().blocksRegeneration())
            .toList();
        if (!inPlay.isEmpty()) {
            throw new ConflictException(
                "MATCHES_HAVE_RESULTS",
                inPlay.size() + " match(es) have already started or finished; the draw can no longer be rebuilt."
            );
        }

        discardDraw(competitionId);
        return draw(competition, request.toGenerateRequest());
    }

    @Transactional(readOnly = true)
    public FixtureSetResponse get(UUID competitionId) {
        Competition competition = competitionService.require(competitionId);

        List<Fixture> fixtures = fixtureRepository.findByCompetitionIdOrderByRoundNumberAsc(competitionId);
        if (fixtures.isEmpty()) {
            throw new ResourceNotFoundException(
                "FIXTURE_NOT_FOUND",
                "This competition has no fixtures yet."
            );
        }

        return toResponse(
            competition,
            fixtures,
            matchAssembler.assemble(matchRepository.findByCompetitionIdOrderByCreatedAtAsc(competitionId))
        );
    }

    /** Everything regeneration throws away, in an order the foreign keys accept. */
    private void discardDraw(UUID competitionId) {
        List<UUID> matchIds = matchRepository.findByCompetitionIdOrderByCreatedAtAsc(competitionId).stream()
            .map(Match::getId)
            .toList();

        leaderboardEntryRepository.deleteByCompetitionId(competitionId);
        if (!matchIds.isEmpty()) {
            resultRepository.deleteByMatchIdIn(matchIds);
        }
        // match_participant is ON DELETE CASCADE, so the lineups go with the matches.
        matchRepository.deleteByCompetitionId(competitionId);
        fixtureRepository.deleteByCompetitionId(competitionId);
        matchRepository.flush();
    }

    private FixtureSetResponse draw(Competition competition, GenerateFixturesRequest request) {
        CompetitionConfigResolver.ResolvedConfig config = competitionConfigResolver.resolve(competition);
        FixtureGenerator generator = fixtureGeneratorFactory.get(config.fixtureGenerator());

        List<SeededParticipant> entrants = approvedEntrants(competition.getId(), request);
        if (entrants.size() < generator.minimumParticipants()) {
            throw new ConflictException(
                "INSUFFICIENT_PARTICIPANTS",
                config.fixtureGenerator() + " needs at least " + generator.minimumParticipants()
                    + " approved entrants; this competition has " + entrants.size() + "."
            );
        }

        FixturePlan plan = generator.generate(
            new FixtureGenerationContext(competition.getId(), entrants, config.rules()));

        if (plan.matchCount() == 0) {
            throw new ConflictException(
                "INSUFFICIENT_PARTICIPANTS",
                config.fixtureGenerator() + " produced no matches from " + entrants.size() + " entrants."
            );
        }

        List<Fixture> fixtures = new ArrayList<>(plan.rounds().size());
        List<Match> matches = new ArrayList<>(plan.matchCount());

        for (FixturePlan.PlannedRound round : plan.rounds()) {
            Fixture fixture = new Fixture();
            fixture.setCompetitionId(competition.getId());
            fixture.setRoundNumber(round.roundNumber());
            fixture.setRoundName(round.roundName());
            fixture.setGeneratorKey(config.fixtureGenerator());
            fixtures.add(fixtureRepository.save(fixture));

            for (FixturePlan.PlannedMatch plannedMatch : round.matches()) {
                Match match = new Match();
                match.setCompetitionId(competition.getId());
                match.setFixtureId(fixture.getId());
                match.setStatus(MatchStatus.SCHEDULED);
                matchRepository.save(match);
                matches.add(match);

                for (FixturePlan.PlannedSlot slot : plannedMatch.slots()) {
                    MatchParticipant participant = new MatchParticipant();
                    participant.setMatchId(match.getId());
                    participant.setParticipantId(slot.participantId());
                    participant.setSlot(slot.slot());
                    participant.setSeed(slot.seed());
                    matchParticipantRepository.save(participant);
                }
            }
        }

        matchRepository.flush();
        return toResponse(competition, fixtures, matchAssembler.assemble(matches));
    }

    /**
     * Only APPROVED registrations are drawn (BR-F-2). Seeding is applied here rather than in the
     * generator so that "who is in the draw and in what order" stays a decision the organizer made,
     * and the generator stays a pure function of the list it is handed.
     */
    private List<SeededParticipant> approvedEntrants(UUID competitionId, GenerateFixturesRequest request) {
        List<Registration> approved = registrationRepository
            .findByCompetitionIdAndStatusAndDeletedAtIsNullOrderBySubmittedAtAsc(
                competitionId, RegistrationStatus.APPROVED);

        Set<UUID> participantIds = new HashSet<>();
        approved.forEach(registration -> participantIds.add(registration.getParticipantId()));

        Map<UUID, String> names = new HashMap<>();
        participantRepository.findAllById(participantIds)
            .forEach(participant -> names.put(participant.getId(), participant.getDisplayName()));

        Map<UUID, Integer> seeds = new HashMap<>();
        if (request.strategyOrDefault() == GenerateFixturesRequest.SeedStrategy.SEEDED) {
            request.seedsOrEmpty().forEach(seed -> seeds.put(seed.participantId(), seed.seed()));
        }

        List<SeededParticipant> entrants = new ArrayList<>(approved.size());
        for (Registration registration : approved) {
            UUID participantId = registration.getParticipantId();
            entrants.add(new SeededParticipant(
                participantId,
                names.getOrDefault(participantId, "Unknown participant"),
                seeds.get(participantId)
            ));
        }

        if (request.strategyOrDefault() == GenerateFixturesRequest.SeedStrategy.SEEDED) {
            // Unseeded entrants keep their submission order behind everyone who was seeded.
            entrants.sort(Comparator.comparing(
                SeededParticipant::seed,
                Comparator.nullsLast(Comparator.naturalOrder())
            ));
        } else {
            Collections.shuffle(entrants);
        }

        return List.copyOf(entrants);
    }

    private FixtureSetResponse toResponse(
        Competition competition,
        List<Fixture> fixtures,
        List<MatchResponse> matches
    ) {
        Map<UUID, List<MatchResponse>> byFixture = new HashMap<>();
        matches.forEach(match ->
            byFixture.computeIfAbsent(match.fixtureId(), key -> new ArrayList<>()).add(match));

        List<FixtureSetResponse.RoundResponse> rounds = fixtures.stream()
            .sorted(Comparator.comparingInt(Fixture::getRoundNumber))
            .map(fixture -> new FixtureSetResponse.RoundResponse(
                fixture.getId(),
                fixture.getRoundNumber(),
                fixture.getRoundName(),
                fixture.getGeneratedAt(),
                byFixture.getOrDefault(fixture.getId(), List.of())
            ))
            .toList();

        return new FixtureSetResponse(
            competition.getId(),
            fixtures.isEmpty() ? null : fixtures.get(0).getGeneratorKey(),
            rounds.size(),
            matches.size(),
            rounds
        );
    }
}
