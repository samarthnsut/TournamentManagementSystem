package com.acme.tms.result.service;

import com.acme.tms.common.audit.Audited;
import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.fixture.domain.Match;
import com.acme.tms.fixture.domain.MatchStatus;
import com.acme.tms.fixture.repository.MatchRepository;
import com.acme.tms.fixture.service.MatchAssembler;
import com.acme.tms.fixture.service.MatchService;
import com.acme.tms.result.domain.Result;
import com.acme.tms.result.dto.RecordResultRequest;
import com.acme.tms.result.dto.RecordResultResponse;
import com.acme.tms.result.repository.ResultRepository;
import com.acme.tms.result.strategy.EvaluatedResult;
import com.acme.tms.result.strategy.MatchContext;
import com.acme.tms.result.strategy.RawResultInput;
import com.acme.tms.result.strategy.ResultEvaluator;
import com.acme.tms.result.strategy.ResultEvaluatorFactory;
import com.acme.tms.result.strategy.ResultOutcome;
import com.acme.tms.tournament.domain.Competition;
import com.acme.tms.tournament.service.CompetitionConfigResolver;
import com.acme.tms.tournament.service.CompetitionService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Records what happened in a match.
 *
 * <p>The result, the match's new status and the recomputed leaderboard all land in one transaction
 * (BR-RES-2). A standings page that lags the result it came from is the kind of inconsistency
 * spectators notice within seconds.
 */
@Service
public class ResultService {

    private final ResultRepository resultRepository;
    private final MatchRepository matchRepository;
    private final MatchService matchService;
    private final MatchAssembler matchAssembler;
    private final CompetitionService competitionService;
    private final CompetitionConfigResolver competitionConfigResolver;
    private final ResultEvaluatorFactory resultEvaluatorFactory;
    private final ResultPayloadCodec resultPayloadCodec;
    private final LeaderboardService leaderboardService;
    private final CurrentUser currentUser;

    public ResultService(
        ResultRepository resultRepository,
        MatchRepository matchRepository,
        MatchService matchService,
        MatchAssembler matchAssembler,
        CompetitionService competitionService,
        CompetitionConfigResolver competitionConfigResolver,
        ResultEvaluatorFactory resultEvaluatorFactory,
        ResultPayloadCodec resultPayloadCodec,
        LeaderboardService leaderboardService,
        CurrentUser currentUser
    ) {
        this.resultRepository = resultRepository;
        this.matchRepository = matchRepository;
        this.matchService = matchService;
        this.matchAssembler = matchAssembler;
        this.competitionService = competitionService;
        this.competitionConfigResolver = competitionConfigResolver;
        this.resultEvaluatorFactory = resultEvaluatorFactory;
        this.resultPayloadCodec = resultPayloadCodec;
        this.leaderboardService = leaderboardService;
        this.currentUser = currentUser;
    }

    @Transactional
    @Audited(value = "result:record", entityType = "Match", entityIdParam = "matchId")
    public RecordResultResponse record(UUID matchId, RecordResultRequest request) {
        Match match = matchService.require(matchId);

        if (match.getStatus().isFinal()) {
            throw new ConflictException(
                "MATCH_ALREADY_COMPLETED",
                "This match is already " + match.getStatus() + "; corrections go through the amendment flow."
            );
        }

        // Two officials at the same table both submit; whoever refetched last is holding a stale
        // version and is told so rather than quietly overwriting the other's entry.
        if (request.version() != null && request.version() != match.getVersion()) {
            throw new ConflictException(
                "STALE_VERSION",
                "This match has changed since you loaded it; refetch and submit again."
            );
        }

        List<UUID> lineup = matchAssembler.participantIdsOf(matchId);
        if (lineup.isEmpty()) {
            throw new ValidationException(
                "MATCH_HAS_NO_PARTICIPANTS",
                "A result cannot be recorded for a match with no participants."
            );
        }

        Competition competition = competitionService.require(match.getCompetitionId());
        CompetitionConfigResolver.ResolvedConfig config = competitionConfigResolver.resolve(competition);
        ResultEvaluator evaluator = resultEvaluatorFactory.get(config.resultEvaluator());

        MatchContext context = new MatchContext(matchId, lineup);
        RawResultInput raw = toRawInput(request);

        evaluator.validate(raw, context, config.rules());
        EvaluatedResult evaluation = evaluator.evaluate(raw, context, config.rules());

        MatchStatus target = request.outcome() == ResultOutcome.WALKOVER
            ? MatchStatus.WALKOVER
            : MatchStatus.COMPLETED;
        if (!match.getStatus().canTransitionTo(target)) {
            throw new ConflictException(
                "INVALID_STATE_TRANSITION",
                "Cannot move a " + match.getStatus() + " match to " + target + "."
            );
        }

        Result result = new Result();
        result.setMatchId(matchId);
        result.setEvaluatorKey(config.resultEvaluator());
        result.setPayload(resultPayloadCodec.encode(new ResultPayload(raw, evaluation)));
        result.setWinnerParticipantId(evaluation.winnerParticipantId());
        result.setRecordedBy(currentUser.requireUserId());
        resultRepository.save(result);

        match.setStatus(target);
        matchRepository.flush();

        leaderboardService.recompute(competition);

        Set<UUID> participantIds = new LinkedHashSet<>(lineup);
        evaluation.participants().forEach(participant -> participantIds.add(participant.participantId()));

        return new RecordResultResponse(
            matchId,
            match.getStatus(),
            match.getVersion(),
            matchAssembler.summarize(result, matchAssembler.displayNames(participantIds))
        );
    }

    private RawResultInput toRawInput(RecordResultRequest request) {
        return new RawResultInput(
            request.outcome(),
            request.scoresOrEmpty().stream()
                .map(score -> new RawResultInput.RawScore(score.participantId(), score.value(), score.unit()))
                .toList(),
            request.winnerParticipantId()
        );
    }
}
