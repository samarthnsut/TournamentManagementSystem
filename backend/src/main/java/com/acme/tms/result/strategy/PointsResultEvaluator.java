package com.acme.tms.result.strategy;

import com.acme.tms.common.domain.SportRules;
import com.acme.tms.common.exception.ValidationException;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Head-to-head scoring: the higher score wins, equal scores draw, and each outcome is worth
 * whatever the tenant's {@code rules} say it is. The points values are read rather than assumed,
 * so a league on two-points-for-a-win needs no code.
 */
@Component
public class PointsResultEvaluator implements ResultEvaluator {

    @Override
    public ResultEvaluatorKey key() {
        return ResultEvaluatorKey.POINTS;
    }

    @Override
    public Set<String> requiredRuleKeys() {
        return Set.of("pointsForWin", "pointsForDraw", "pointsForLoss");
    }

    @Override
    public void validate(RawResultInput input, MatchContext match, SportRules rules) {
        List<String> problems = new ArrayList<>();

        if (match.participantIds().size() != 2) {
            problems.add("a POINTS match needs exactly two participants, found " + match.participantIds().size());
        }

        if (input.outcome() == ResultOutcome.WALKOVER) {
            if (input.declaredWinnerParticipantId() == null) {
                problems.add("a walkover must declare the winner");
            } else if (!match.participantIds().contains(input.declaredWinnerParticipantId())) {
                problems.add("the declared winner is not in this match");
            }
            failIfAny(problems);
            return;
        }

        List<RawResultInput.RawScore> scores = input.scores() == null ? List.of() : input.scores();
        Set<UUID> scored = new HashSet<>();

        for (RawResultInput.RawScore score : scores) {
            if (!match.participantIds().contains(score.participantId())) {
                problems.add("participant " + score.participantId() + " is not in this match");
                continue;
            }
            if (!scored.add(score.participantId())) {
                problems.add("participant " + score.participantId() + " was scored more than once");
            }
            if (score.value() == null) {
                problems.add("a score is required for participant " + score.participantId());
            } else if (score.value().signum() < 0) {
                problems.add("a score cannot be negative for participant " + score.participantId());
            }
        }

        for (UUID participantId : match.participantIds()) {
            if (!scored.contains(participantId)) {
                problems.add("no score was recorded for participant " + participantId);
            }
        }

        failIfAny(problems);
    }

    @Override
    public EvaluatedResult evaluate(RawResultInput input, MatchContext match, SportRules rules) {
        BigDecimal pointsForWin = rules.getDecimal("pointsForWin", BigDecimal.valueOf(3));
        BigDecimal pointsForDraw = rules.getDecimal("pointsForDraw", BigDecimal.ONE);
        BigDecimal pointsForLoss = rules.getDecimal("pointsForLoss", BigDecimal.ZERO);

        if (input.outcome() == ResultOutcome.WALKOVER) {
            UUID winnerId = input.declaredWinnerParticipantId();
            List<ParticipantResult> participants = new ArrayList<>(match.participantIds().size());
            for (UUID participantId : match.participantIds()) {
                boolean won = participantId.equals(winnerId);
                participants.add(new ParticipantResult(
                    participantId,
                    null,
                    null,
                    won ? pointsForWin : pointsForLoss,
                    won ? ParticipantResult.Standing.WIN : ParticipantResult.Standing.LOSS
                ));
            }
            return new EvaluatedResult(winnerId, List.copyOf(participants));
        }

        UUID firstId = match.participantIds().get(0);
        UUID secondId = match.participantIds().get(1);
        BigDecimal firstScore = scoreOf(input, firstId);
        BigDecimal secondScore = scoreOf(input, secondId);

        int comparison = firstScore.compareTo(secondScore);
        UUID winnerId = comparison > 0 ? firstId : comparison < 0 ? secondId : null;

        return new EvaluatedResult(winnerId, List.of(
            outcomeFor(firstId, firstScore, comparison, pointsForWin, pointsForDraw, pointsForLoss),
            outcomeFor(secondId, secondScore, -comparison, pointsForWin, pointsForDraw, pointsForLoss)
        ));
    }

    private ParticipantResult outcomeFor(
        UUID participantId,
        BigDecimal score,
        int comparison,
        BigDecimal pointsForWin,
        BigDecimal pointsForDraw,
        BigDecimal pointsForLoss
    ) {
        ParticipantResult.Standing standing = comparison > 0
            ? ParticipantResult.Standing.WIN
            : comparison < 0 ? ParticipantResult.Standing.LOSS : ParticipantResult.Standing.DRAW;
        BigDecimal points = switch (standing) {
            case WIN -> pointsForWin;
            case LOSS -> pointsForLoss;
            default -> pointsForDraw;
        };
        return new ParticipantResult(participantId, score, null, points, standing);
    }

    private BigDecimal scoreOf(RawResultInput input, UUID participantId) {
        return input.scores().stream()
            .filter(score -> participantId.equals(score.participantId()))
            .map(RawResultInput.RawScore::value)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("validate() should have rejected a missing score"));
    }

    private void failIfAny(List<String> problems) {
        if (!problems.isEmpty()) {
            throw new ValidationException("INVALID_RESULT", String.join("; ", problems));
        }
    }
}
