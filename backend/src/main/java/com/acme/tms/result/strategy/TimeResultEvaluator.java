package com.acme.tms.result.strategy;

import com.acme.tms.common.domain.SportRules;
import com.acme.tms.common.exception.ValidationException;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A recorded time, lowest wins. Unlike a head-to-head evaluator this happily takes a whole field at
 * once — a heat of eight is one match with eight timed entrants.
 *
 * <p>An entrant with no time (did not start, did not finish, disqualified) is recorded as
 * {@code NO_CONTEST} rather than being dropped, so the field that lined up is still visible after
 * the race.
 */
@Component
public class TimeResultEvaluator implements ResultEvaluator {

    @Override
    public ResultEvaluatorKey key() {
        return ResultEvaluatorKey.TIME;
    }

    @Override
    public Set<String> requiredRuleKeys() {
        return Set.of("timeUnit", "precision");
    }

    @Override
    public void validate(RawResultInput input, MatchContext match, SportRules rules) {
        List<String> problems = new ArrayList<>();

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
        if (scores.isEmpty()) {
            problems.add("at least one time is required");
        }

        String expectedUnit = rules.getString("timeUnit", null);
        BigDecimal lowerBound = rules.getDecimal("recordLowerBoundSeconds", null);
        Set<UUID> timed = new HashSet<>();

        for (RawResultInput.RawScore score : scores) {
            if (!match.participantIds().contains(score.participantId())) {
                problems.add("participant " + score.participantId() + " is not in this match");
                continue;
            }
            if (!timed.add(score.participantId())) {
                problems.add("participant " + score.participantId() + " was timed more than once");
            }
            if (score.unit() != null && expectedUnit != null && !score.unit().equalsIgnoreCase(expectedUnit)) {
                problems.add("times must be recorded in " + expectedUnit + ", got " + score.unit());
            }
            if (score.value() == null) {
                // A null time is how "did not finish" is recorded; nothing further to check.
                continue;
            }
            if (score.value().signum() <= 0) {
                problems.add("a time must be positive for participant " + score.participantId());
            } else if (lowerBound != null && score.value().compareTo(lowerBound) < 0) {
                // Faster than the standing world record is a typo, not a performance.
                problems.add("time " + score.value() + " for participant " + score.participantId()
                    + " is below the configured lower bound of " + lowerBound);
            }
        }

        failIfAny(problems);
    }

    @Override
    public EvaluatedResult evaluate(RawResultInput input, MatchContext match, SportRules rules) {
        String unit = rules.getString("timeUnit", "SECONDS");

        if (input.outcome() == ResultOutcome.WALKOVER) {
            UUID winnerId = input.declaredWinnerParticipantId();
            List<ParticipantResult> participants = new ArrayList<>(match.participantIds().size());
            for (UUID participantId : match.participantIds()) {
                boolean won = participantId.equals(winnerId);
                participants.add(new ParticipantResult(
                    participantId,
                    null,
                    unit,
                    BigDecimal.ZERO,
                    won ? ParticipantResult.Standing.WIN : ParticipantResult.Standing.NO_CONTEST
                ));
            }
            return new EvaluatedResult(winnerId, List.copyOf(participants));
        }

        int precision = Math.max(0, rules.getInt("precision", 2));

        List<ParticipantResult> participants = new ArrayList<>(input.scores().size());
        UUID fastestId = null;
        BigDecimal fastestTime = null;

        for (RawResultInput.RawScore score : input.scores()) {
            // Timing gear reports more digits than the sport recognizes; rounding here means the
            // stored result and the leaderboard can never disagree about who was quicker.
            BigDecimal time = score.value() == null
                ? null
                : score.value().setScale(precision, RoundingMode.HALF_UP);

            participants.add(new ParticipantResult(
                score.participantId(),
                time,
                unit,
                BigDecimal.ZERO,
                time == null ? ParticipantResult.Standing.NO_CONTEST : ParticipantResult.Standing.RANKED
            ));

            if (time != null && (fastestTime == null || time.compareTo(fastestTime) < 0)) {
                fastestTime = time;
                fastestId = score.participantId();
            }
        }

        return new EvaluatedResult(fastestId, List.copyOf(participants));
    }

    private void failIfAny(List<String> problems) {
        if (!problems.isEmpty()) {
            throw new ValidationException("INVALID_RESULT", String.join("; ", problems));
        }
    }
}
