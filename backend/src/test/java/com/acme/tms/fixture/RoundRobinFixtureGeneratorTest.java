package com.acme.tms.fixture;

import com.acme.tms.common.domain.SportRules;
import com.acme.tms.fixture.strategy.FixtureGenerationContext;
import com.acme.tms.fixture.strategy.FixturePlan;
import com.acme.tms.fixture.strategy.RoundRobinFixtureGenerator;
import com.acme.tms.fixture.strategy.SeededParticipant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pairing properties a round robin has to hold, whatever the entry count: everyone meets
 * everyone exactly once, nobody plays twice in a round, and an odd field costs one entrant a rest
 * rather than producing a match against nobody.
 */
class RoundRobinFixtureGeneratorTest {

    private final RoundRobinFixtureGenerator generator = new RoundRobinFixtureGenerator();

    private FixturePlan generate(int entrantCount, int legs) {
        List<SeededParticipant> entrants = new ArrayList<>(entrantCount);
        for (int index = 1; index <= entrantCount; index++) {
            entrants.add(SeededParticipant.unseeded(UUID.randomUUID(), "Team " + index));
        }
        return generator.generate(new FixtureGenerationContext(UUID.randomUUID(), entrants, rules(legs)));
    }

    private SportRules rules(int legs) {
        return SportRules.of(new ObjectMapper().createObjectNode().put("legs", legs));
    }

    /** The worked example in the Sprint 6 definition of done. */
    @Test
    void fiveTeamsPlayTenMatchesAcrossFiveRounds() {
        FixturePlan plan = generate(5, 1);

        assertThat(plan.rounds()).hasSize(5);
        assertThat(plan.matchCount()).isEqualTo(10);
    }

    @ParameterizedTest(name = "{0} entrants produce {1} matches over {2} rounds")
    @CsvSource({
        "2, 1, 1",
        "3, 3, 3",
        "4, 6, 3",
        "5, 10, 5",
        "6, 15, 5",
        "8, 28, 7",
        "9, 36, 9"
    })
    void everyFieldSizeProducesTheCombinatorialNumberOfMatches(int entrants, int matches, int rounds) {
        FixturePlan plan = generate(entrants, 1);

        assertThat(plan.matchCount()).isEqualTo(matches);
        assertThat(plan.rounds()).hasSize(rounds);
    }

    @Test
    void everyPairMeetsExactlyOnce() {
        FixturePlan plan = generate(7, 1);

        Map<Set<UUID>, Integer> meetings = new HashMap<>();
        for (FixturePlan.PlannedRound round : plan.rounds()) {
            for (FixturePlan.PlannedMatch match : round.matches()) {
                Set<UUID> pair = new HashSet<>();
                match.slots().forEach(slot -> pair.add(slot.participantId()));
                meetings.merge(pair, 1, Integer::sum);
            }
        }

        assertThat(meetings).hasSize(21);
        assertThat(meetings.values()).allMatch(count -> count == 1);
    }

    @Test
    void nobodyIsDrawnTwiceInTheSameRound() {
        FixturePlan plan = generate(9, 1);

        for (FixturePlan.PlannedRound round : plan.rounds()) {
            List<UUID> appearances = new ArrayList<>();
            round.matches().forEach(match ->
                match.slots().forEach(slot -> appearances.add(slot.participantId())));

            assertThat(appearances)
                .as("round %d", round.roundNumber())
                .doesNotHaveDuplicates();
        }
    }

    @Test
    void anOddFieldRestsOneEntrantPerRoundRatherThanPairingThemWithNobody() {
        FixturePlan plan = generate(5, 1);

        for (FixturePlan.PlannedRound round : plan.rounds()) {
            assertThat(round.matches()).as("round %d", round.roundNumber()).hasSize(2);
            round.matches().forEach(match -> {
                assertThat(match.slots()).hasSize(2);
                assertThat(match.slots()).noneMatch(slot -> slot.participantId() == null);
            });
        }
    }

    @Test
    void aSecondLegDoublesTheScheduleAndReversesHomeAdvantage() {
        FixturePlan plan = generate(4, 2);

        assertThat(plan.rounds()).hasSize(6);
        assertThat(plan.matchCount()).isEqualTo(12);

        Map<Set<UUID>, Integer> meetings = new HashMap<>();
        Map<List<UUID>, Integer> orderedMeetings = new HashMap<>();
        for (FixturePlan.PlannedRound round : plan.rounds()) {
            for (FixturePlan.PlannedMatch match : round.matches()) {
                List<UUID> ordered = match.slots().stream().map(FixturePlan.PlannedSlot::participantId).toList();
                orderedMeetings.merge(ordered, 1, Integer::sum);
                meetings.merge(new HashSet<>(ordered), 1, Integer::sum);
            }
        }

        assertThat(meetings.values()).as("each pair meets twice").allMatch(count -> count == 2);
        assertThat(orderedMeetings.values())
            .as("and never twice with the same side at home")
            .allMatch(count -> count == 1);
    }

    @Test
    void everySlotIsLabelledHomeOrAway() {
        FixturePlan plan = generate(6, 1);

        for (FixturePlan.PlannedRound round : plan.rounds()) {
            for (FixturePlan.PlannedMatch match : round.matches()) {
                assertThat(match.slots()).extracting(FixturePlan.PlannedSlot::slot)
                    .containsExactly("HOME", "AWAY");
            }
        }
    }

    @Test
    void roundNumbersAreContiguousFromOne() {
        FixturePlan plan = generate(6, 2);

        assertThat(plan.rounds()).extracting(FixturePlan.PlannedRound::roundNumber)
            .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void aFieldTooSmallToPairProducesNothing() {
        assertThat(generate(1, 1).rounds()).isEmpty();
    }

    @Test
    void theSameEntrantListAlwaysProducesTheSameDraw() {
        // Regeneration has to be predictable; any shuffling is the service's decision, not the
        // generator's (which is what lets the service offer RANDOM and SEEDED from one generator).
        List<SeededParticipant> entrants = List.of(
            SeededParticipant.unseeded(UUID.randomUUID(), "A"),
            SeededParticipant.unseeded(UUID.randomUUID(), "B"),
            SeededParticipant.unseeded(UUID.randomUUID(), "C"),
            SeededParticipant.unseeded(UUID.randomUUID(), "D")
        );
        UUID competitionId = UUID.randomUUID();

        FixturePlan first = generator.generate(new FixtureGenerationContext(competitionId, entrants, rules(1)));
        FixturePlan second = generator.generate(new FixtureGenerationContext(competitionId, entrants, rules(1)));

        assertThat(first).isEqualTo(second);
    }
}
