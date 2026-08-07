package com.acme.tms.fixture;

import com.acme.tms.common.domain.SportRules;
import com.acme.tms.fixture.strategy.FixtureGenerationContext;
import com.acme.tms.fixture.strategy.FixturePlan;
import com.acme.tms.fixture.strategy.NoneFixtureGenerator;
import com.acme.tms.fixture.strategy.SeededParticipant;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A measured event has nothing to pair, but it still has to produce a plan — the shell is what
 * stops result and leaderboard code from ever asking "does this competition have fixtures?".
 */
class NoneFixtureGeneratorTest {

    private final NoneFixtureGenerator generator = new NoneFixtureGenerator();

    private FixturePlan generate(List<SeededParticipant> entrants) {
        return generator.generate(
            new FixtureGenerationContext(UUID.randomUUID(), entrants, SportRules.empty()));
    }

    private List<SeededParticipant> runners(int count) {
        List<SeededParticipant> entrants = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            entrants.add(SeededParticipant.unseeded(UUID.randomUUID(), "Runner " + index));
        }
        return entrants;
    }

    @Test
    void eightRunnersBecomeOneFinalWithEightLanes() {
        FixturePlan plan = generate(runners(8));

        assertThat(plan.rounds()).hasSize(1);
        assertThat(plan.matchCount()).isEqualTo(1);

        FixturePlan.PlannedRound round = plan.rounds().get(0);
        assertThat(round.roundNumber()).isEqualTo(1);
        assertThat(round.roundName()).isEqualTo("Final");
        assertThat(round.matches().get(0).slots()).hasSize(8);
    }

    @Test
    void lanesAreNumberedFromOne() {
        FixturePlan plan = generate(runners(4));

        assertThat(plan.rounds().get(0).matches().get(0).slots())
            .extracting(FixturePlan.PlannedSlot::slot)
            .containsExactly("LANE_1", "LANE_2", "LANE_3", "LANE_4");
    }

    @Test
    void seededRunnersTakeTheInsideLanes() {
        UUID fastest = UUID.randomUUID();
        UUID middle = UUID.randomUUID();
        UUID unseeded = UUID.randomUUID();

        FixturePlan plan = generate(List.of(
            new SeededParticipant(unseeded, "Unseeded", null),
            new SeededParticipant(middle, "Middle", 2),
            new SeededParticipant(fastest, "Fastest", 1)
        ));

        assertThat(plan.rounds().get(0).matches().get(0).slots())
            .extracting(FixturePlan.PlannedSlot::participantId)
            .containsExactly(fastest, middle, unseeded);
    }

    @Test
    void aSoloTimeTrialIsStillAValidEvent() {
        // Unlike a head-to-head format, one entrant against the clock is a real competition.
        assertThat(generator.minimumParticipants()).isEqualTo(1);
        assertThat(generate(runners(1)).matchCount()).isEqualTo(1);
    }

    @Test
    void anEmptyFieldProducesNothing() {
        assertThat(generate(List.of()).rounds()).isEmpty();
    }
}
