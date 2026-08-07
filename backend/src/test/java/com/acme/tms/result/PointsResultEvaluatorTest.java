package com.acme.tms.result;

import com.acme.tms.common.domain.SportRules;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.result.strategy.EvaluatedResult;
import com.acme.tms.result.strategy.MatchContext;
import com.acme.tms.result.strategy.ParticipantResult;
import com.acme.tms.result.strategy.PointsResultEvaluator;
import com.acme.tms.result.strategy.RawResultInput;
import com.acme.tms.result.strategy.ResultOutcome;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointsResultEvaluatorTest {

    private static final UUID HOME = UUID.randomUUID();
    private static final UUID AWAY = UUID.randomUUID();

    private final PointsResultEvaluator evaluator = new PointsResultEvaluator();
    private final MatchContext match = new MatchContext(UUID.randomUUID(), List.of(HOME, AWAY));

    private SportRules rules() {
        return rules(3, 1, 0);
    }

    private SportRules rules(int win, int draw, int loss) {
        return SportRules.of(new ObjectMapper().createObjectNode()
            .put("pointsForWin", win)
            .put("pointsForDraw", draw)
            .put("pointsForLoss", loss));
    }

    private RawResultInput scored(int homeGoals, int awayGoals) {
        return new RawResultInput(ResultOutcome.COMPLETED, List.of(
            new RawResultInput.RawScore(HOME, BigDecimal.valueOf(homeGoals), null),
            new RawResultInput.RawScore(AWAY, BigDecimal.valueOf(awayGoals), null)
        ), null);
    }

    private ParticipantResult resultFor(EvaluatedResult evaluation, UUID participantId) {
        return evaluation.participants().stream()
            .filter(participant -> participant.participantId().equals(participantId))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void theHigherScoreWins() {
        EvaluatedResult evaluation = evaluator.evaluate(scored(2, 1), match, rules());

        assertThat(evaluation.winnerParticipantId()).isEqualTo(HOME);
        assertThat(resultFor(evaluation, HOME).standing()).isEqualTo(ParticipantResult.Standing.WIN);
        assertThat(resultFor(evaluation, HOME).points()).isEqualByComparingTo("3");
        assertThat(resultFor(evaluation, AWAY).standing()).isEqualTo(ParticipantResult.Standing.LOSS);
        assertThat(resultFor(evaluation, AWAY).points()).isEqualByComparingTo("0");
    }

    @Test
    void anAwayWinIsReadTheSameWay() {
        EvaluatedResult evaluation = evaluator.evaluate(scored(0, 3), match, rules());

        assertThat(evaluation.winnerParticipantId()).isEqualTo(AWAY);
        assertThat(resultFor(evaluation, AWAY).standing()).isEqualTo(ParticipantResult.Standing.WIN);
    }

    @Test
    void equalScoresDrawAndLeaveNoWinner() {
        EvaluatedResult evaluation = evaluator.evaluate(scored(1, 1), match, rules());

        assertThat(evaluation.winnerParticipantId()).isNull();
        assertThat(evaluation.participants())
            .extracting(ParticipantResult::standing)
            .containsOnly(ParticipantResult.Standing.DRAW);
        assertThat(resultFor(evaluation, HOME).points()).isEqualByComparingTo("1");
    }

    @Test
    void thePointsComeFromTheRulesRatherThanFromTheCode() {
        // A two-points-for-a-win league is a configuration change, not a code change.
        EvaluatedResult evaluation = evaluator.evaluate(scored(2, 1), match, rules(2, 1, 0));

        assertThat(resultFor(evaluation, HOME).points()).isEqualByComparingTo("2");
    }

    @Test
    void aWalkoverAwardsTheWinWithoutAScore() {
        RawResultInput walkover = new RawResultInput(ResultOutcome.WALKOVER, List.of(), AWAY);

        evaluator.validate(walkover, match, rules());
        EvaluatedResult evaluation = evaluator.evaluate(walkover, match, rules());

        assertThat(evaluation.winnerParticipantId()).isEqualTo(AWAY);
        assertThat(resultFor(evaluation, AWAY).standing()).isEqualTo(ParticipantResult.Standing.WIN);
        assertThat(resultFor(evaluation, AWAY).points()).isEqualByComparingTo("3");
        assertThat(resultFor(evaluation, AWAY).value()).isNull();
        assertThat(resultFor(evaluation, HOME).standing()).isEqualTo(ParticipantResult.Standing.LOSS);
    }

    @Test
    void aWalkoverWithoutADeclaredWinnerIsRejected() {
        RawResultInput walkover = new RawResultInput(ResultOutcome.WALKOVER, List.of(), null);

        assertThatThrownBy(() -> evaluator.validate(walkover, match, rules()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("declare the winner");
    }

    @Test
    void aWalkoverToSomebodyOutsideTheMatchIsRejected() {
        RawResultInput walkover = new RawResultInput(ResultOutcome.WALKOVER, List.of(), UUID.randomUUID());

        assertThatThrownBy(() -> evaluator.validate(walkover, match, rules()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("not in this match");
    }

    @Test
    void aMissingScoreIsRejected() {
        RawResultInput partial = new RawResultInput(ResultOutcome.COMPLETED, List.of(
            new RawResultInput.RawScore(HOME, BigDecimal.ONE, null)
        ), null);

        assertThatThrownBy(() -> evaluator.validate(partial, match, rules()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("no score was recorded");
    }

    @Test
    void aNegativeScoreIsRejected() {
        RawResultInput negative = new RawResultInput(ResultOutcome.COMPLETED, List.of(
            new RawResultInput.RawScore(HOME, BigDecimal.valueOf(-1), null),
            new RawResultInput.RawScore(AWAY, BigDecimal.ONE, null)
        ), null);

        assertThatThrownBy(() -> evaluator.validate(negative, match, rules()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("cannot be negative");
    }

    @Test
    void scoringSomebodyWhoIsNotPlayingIsRejected() {
        RawResultInput stray = new RawResultInput(ResultOutcome.COMPLETED, List.of(
            new RawResultInput.RawScore(HOME, BigDecimal.ONE, null),
            new RawResultInput.RawScore(UUID.randomUUID(), BigDecimal.ONE, null)
        ), null);

        assertThatThrownBy(() -> evaluator.validate(stray, match, rules()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("is not in this match");
    }

    @Test
    void aHeadToHeadEvaluatorRefusesAFieldOfMoreThanTwo() {
        MatchContext race = new MatchContext(UUID.randomUUID(), List.of(HOME, AWAY, UUID.randomUUID()));

        assertThatThrownBy(() -> evaluator.validate(scored(1, 0), race, rules()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("exactly two participants");
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        // An official fixing one mistake at a time per round trip is how data entry gets abandoned.
        RawResultInput bad = new RawResultInput(ResultOutcome.COMPLETED, List.of(
            new RawResultInput.RawScore(HOME, BigDecimal.valueOf(-1), null),
            new RawResultInput.RawScore(UUID.randomUUID(), BigDecimal.ONE, null)
        ), null);

        assertThatThrownBy(() -> evaluator.validate(bad, match, rules()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("cannot be negative")
            .hasMessageContaining("is not in this match")
            .hasMessageContaining("no score was recorded");
    }
}
