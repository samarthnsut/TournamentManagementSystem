package com.acme.tms.fixture.strategy;

import com.acme.tms.common.domain.ParticipantType;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Everyone plays everyone, paired by the circle method: one entrant is pinned and the rest rotate
 * around them, which yields n-1 rounds where each entrant appears exactly once per round.
 *
 * <p>An odd entry list gets a null padding slot — the entrant drawn against it sits that round out.
 * Dropping the pairing rather than persisting a one-sided match keeps "a match has two sides" true
 * everywhere downstream, at the cost of rounds that are one match short.
 */
@Component
public class RoundRobinFixtureGenerator implements FixtureGenerator {

    @Override
    public FixtureGeneratorKey key() {
        return FixtureGeneratorKey.ROUND_ROBIN;
    }

    @Override
    public boolean supports(ParticipantType participantType) {
        return true;
    }

    @Override
    public Set<String> requiredRuleKeys() {
        return Set.of("legs");
    }

    @Override
    public FixturePlan generate(FixtureGenerationContext context) {
        List<SeededParticipant> entrants = new ArrayList<>(context.participants());
        if (entrants.size() < minimumParticipants()) {
            return FixturePlan.empty();
        }

        // The pinned entrant never moves, so an odd list is padded to keep the halves equal.
        boolean hasBye = entrants.size() % 2 != 0;
        if (hasBye) {
            entrants.add(null);
        }

        int size = entrants.size();
        int roundsPerLeg = size - 1;
        int pairingsPerRound = size / 2;
        int legs = Math.max(1, context.rules().getInt("legs", 1));

        List<FixturePlan.PlannedRound> rounds = new ArrayList<>(roundsPerLeg * legs);

        for (int leg = 1; leg <= legs; leg++) {
            List<SeededParticipant> rota = new ArrayList<>(entrants);

            for (int round = 0; round < roundsPerLeg; round++) {
                List<FixturePlan.PlannedMatch> matches = new ArrayList<>(pairingsPerRound);

                for (int pairing = 0; pairing < pairingsPerRound; pairing++) {
                    SeededParticipant first = rota.get(pairing);
                    SeededParticipant second = rota.get(size - 1 - pairing);
                    if (first == null || second == null) {
                        continue;
                    }

                    // Alternating by round stops the pinned entrant from being at home every week;
                    // alternating by leg is what makes a second leg a return fixture.
                    boolean swapSides = (round % 2 == 1) ^ (leg % 2 == 0);
                    SeededParticipant home = swapSides ? second : first;
                    SeededParticipant away = swapSides ? first : second;

                    matches.add(new FixturePlan.PlannedMatch(List.of(
                        slot(home, "HOME"),
                        slot(away, "AWAY")
                    )));
                }

                int roundNumber = (leg - 1) * roundsPerLeg + round + 1;
                rounds.add(new FixturePlan.PlannedRound(roundNumber, roundName(roundNumber, leg, legs), matches));
                rotate(rota);
            }
        }

        return new FixturePlan(List.copyOf(rounds));
    }

    /** Pin index 0 and walk everyone else one place around the circle. */
    private void rotate(List<SeededParticipant> rota) {
        rota.add(1, rota.remove(rota.size() - 1));
    }

    private String roundName(int roundNumber, int leg, int legs) {
        return legs > 1 ? "Round " + roundNumber + " (Leg " + leg + ")" : "Round " + roundNumber;
    }

    private FixturePlan.PlannedSlot slot(SeededParticipant participant, String slot) {
        return new FixturePlan.PlannedSlot(participant.participantId(), slot, participant.seed());
    }
}
