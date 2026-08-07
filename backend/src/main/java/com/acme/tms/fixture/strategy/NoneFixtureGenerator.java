package com.acme.tms.fixture.strategy;

import com.acme.tms.common.domain.ParticipantType;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * For measured events such as a 100m final, where there is nothing to pair: every entrant competes
 * at once and the clock separates them.
 *
 * <p>It still emits a plan — one round holding one match with every entrant in a lane — so results
 * and leaderboards read the same shape here as they do for a league, and no downstream code has to
 * special-case "this competition has no fixtures".
 */
@Component
public class NoneFixtureGenerator implements FixtureGenerator {

    @Override
    public FixtureGeneratorKey key() {
        return FixtureGeneratorKey.NONE;
    }

    @Override
    public boolean supports(ParticipantType participantType) {
        return true;
    }

    /** A time trial with a single entrant is still a valid event. */
    @Override
    public int minimumParticipants() {
        return 1;
    }

    @Override
    public FixturePlan generate(FixtureGenerationContext context) {
        if (context.participants().size() < minimumParticipants()) {
            return FixturePlan.empty();
        }

        // Seeded entrants get the inside lanes; an unseeded draw keeps its given order.
        List<SeededParticipant> entrants = new ArrayList<>(context.participants());
        entrants.sort(Comparator.comparing(
            SeededParticipant::seed,
            Comparator.nullsLast(Comparator.naturalOrder())
        ));

        List<FixturePlan.PlannedSlot> lanes = new ArrayList<>(entrants.size());
        for (int lane = 0; lane < entrants.size(); lane++) {
            SeededParticipant entrant = entrants.get(lane);
            lanes.add(new FixturePlan.PlannedSlot(entrant.participantId(), "LANE_" + (lane + 1), entrant.seed()));
        }

        return new FixturePlan(List.of(new FixturePlan.PlannedRound(
            1,
            "Final",
            List.of(new FixturePlan.PlannedMatch(List.copyOf(lanes)))
        )));
    }
}
