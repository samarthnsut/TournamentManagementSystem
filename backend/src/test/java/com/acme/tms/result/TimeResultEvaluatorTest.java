package com.acme.tms.result;

import com.acme.tms.common.domain.SportRules;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.result.strategy.EvaluatedResult;
import com.acme.tms.result.strategy.MatchContext;
import com.acme.tms.result.strategy.ParticipantResult;
import com.acme.tms.result.strategy.RawResultInput;
import com.acme.tms.result.strategy.ResultOutcome;
import com.acme.tms.result.strategy.TimeResultEvaluator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeResultEvaluatorTest {

    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static final UUID THIRD = UUID.randomUUID();

    private final TimeResultEvaluator evaluator = new TimeResultEvaluator();
    private final MatchContext heat = new MatchContext(UUID.randomUUID(), List.of(FIRST, SECOND, THIRD));

    private SportRules rules() {
        return SportRules.of(new ObjectMapper().createObjectNode()
            .put("timeUnit", "SECONDS")
            .put("precision", 3));
    }

    private SportRules rulesWithLowerBound(String bound) {
        return SportRules.of(new ObjectMapper().createObjectNode()
            .put("timeUnit", "SECONDS")
            .put("precision", 3)
            .put("recordLowerBoundSeconds", new BigDecimal(bound)));
    }

    private RawResultInput times(String first, String second, String third) {
        List<RawResultInput.RawScore> scores = new ArrayList<>();
        scores.add(new RawResultInput.RawScore(FIRST, first == null ? null : new BigDecimal(first), "SECONDS"));
        scores.add(new RawResultInput.RawScore(SECOND, second == null ? null : new BigDecimal(second), "SECONDS"));
        scores.add(new RawResultInput.RawScore(THIRD, third == null ? null : new BigDecimal(third), "SECONDS"));
        return new RawResultInput(ResultOutcome.COMPLETED, scores, null);
    }

    private ParticipantResult resultFor(EvaluatedResult evaluation, UUID participantId) {
        return evaluation.participants().stream()
            .filter(participant -> participant.participantId().equals(participantId))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void theLowestTimeWins() {
        EvaluatedResult evaluation = evaluator.evaluate(times("11.42", "10.98", "12.01"), heat, rules());

        assertThat(evaluation.winnerParticipantId()).isEqualTo(SECOND);
    }

    @Test
    void aWholeFieldIsTimedInOneMatch() {
        EvaluatedResult evaluation = evaluator.evaluate(times("11.42", "10.98", "12.01"), heat, rules());

        assertThat(evaluation.participants()).hasSize(3);
        assertThat(evaluation.participants())
            .extracting(ParticipantResult::standing)
            .containsOnly(ParticipantResult.Standing.RANKED);
    }

    @Test
    void timesAreRoundedToTheConfiguredPrecision() {
        // Timing gear reports more digits than the sport recognizes; storing the rounded value is
        // what stops the board disagreeing with the result it came from.
        EvaluatedResult evaluation = evaluator.evaluate(
            times("11.4249", "10.9876", "12.0"), heat, rules());

        assertThat(resultFor(evaluation, FIRST).value()).isEqualByComparingTo("11.425");
        assertThat(resultFor(evaluation, SECOND).value()).isEqualByComparingTo("10.988");
        assertThat(resultFor(evaluation, THIRD).value()).isEqualByComparingTo("12.000");
    }

    @Test
    void aRunnerWithNoTimeIsKeptOnTheSheetAsNoContest() {
        EvaluatedResult evaluation = evaluator.evaluate(times("11.42", null, "12.01"), heat, rules());

        assertThat(resultFor(evaluation, SECOND).standing()).isEqualTo(ParticipantResult.Standing.NO_CONTEST);
        assertThat(resultFor(evaluation, SECOND).value()).isNull();
        assertThat(evaluation.winnerParticipantId()).isEqualTo(FIRST);
    }

    @Test
    void theUnitIsCarriedThroughToTheResult() {
        EvaluatedResult evaluation = evaluator.evaluate(times("11.42", "10.98", "12.01"), heat, rules());

        assertThat(evaluation.participants()).extracting(ParticipantResult::unit).containsOnly("SECONDS");
    }

    @Test
    void aTimedEventAwardsNoPoints() {
        EvaluatedResult evaluation = evaluator.evaluate(times("11.42", "10.98", "12.01"), heat, rules());

        assertThat(evaluation.participants())
            .allSatisfy(participant -> assertThat(participant.points()).isEqualByComparingTo("0"));
    }

    @Test
    void aNegativeOrZeroTimeIsRejected() {
        assertThatThrownBy(() -> evaluator.validate(times("-1.0", "10.98", "12.01"), heat, rules()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must be positive");
    }

    @Test
    void aTimeFasterThanTheConfiguredRecordIsTreatedAsATypo() {
        assertThatThrownBy(() ->
            evaluator.validate(times("8.5", "10.98", "12.01"), heat, rulesWithLowerBound("9.0")))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("below the configured lower bound");
    }

    @Test
    void aTimeInTheWrongUnitIsRejected() {
        RawResultInput wrongUnit = new RawResultInput(ResultOutcome.COMPLETED, List.of(
            new RawResultInput.RawScore(FIRST, new BigDecimal("11.42"), "MINUTES")
        ), null);

        assertThatThrownBy(() -> evaluator.validate(wrongUnit, heat, rules()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must be recorded in SECONDS");
    }

    @Test
    void timingSomebodyWhoIsNotInTheHeatIsRejected() {
        RawResultInput stray = new RawResultInput(ResultOutcome.COMPLETED, List.of(
            new RawResultInput.RawScore(UUID.randomUUID(), new BigDecimal("11.42"), "SECONDS")
        ), null);

        assertThatThrownBy(() -> evaluator.validate(stray, heat, rules()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("is not in this match");
    }

    @Test
    void anEmptySubmissionIsRejected() {
        RawResultInput empty = new RawResultInput(ResultOutcome.COMPLETED, List.of(), null);

        assertThatThrownBy(() -> evaluator.validate(empty, heat, rules()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("at least one time is required");
    }

    @Test
    void aRaceNobodyElseTurnedUpForIsAWalkover() {
        RawResultInput walkover = new RawResultInput(ResultOutcome.WALKOVER, List.of(), FIRST);

        evaluator.validate(walkover, heat, rules());
        EvaluatedResult evaluation = evaluator.evaluate(walkover, heat, rules());

        assertThat(evaluation.winnerParticipantId()).isEqualTo(FIRST);
        assertThat(resultFor(evaluation, FIRST).standing()).isEqualTo(ParticipantResult.Standing.WIN);
        assertThat(resultFor(evaluation, SECOND).standing()).isEqualTo(ParticipantResult.Standing.NO_CONTEST);
    }
}
