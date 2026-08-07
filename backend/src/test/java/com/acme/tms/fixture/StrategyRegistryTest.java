package com.acme.tms.fixture;

import com.acme.tms.common.domain.ParticipantType;
import com.acme.tms.common.domain.SportRules;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.fixture.strategy.FixtureGenerationContext;
import com.acme.tms.fixture.strategy.FixtureGenerator;
import com.acme.tms.fixture.strategy.FixtureGeneratorFactory;
import com.acme.tms.fixture.strategy.FixtureGeneratorKey;
import com.acme.tms.fixture.strategy.FixturePlan;
import com.acme.tms.fixture.strategy.NoneFixtureGenerator;
import com.acme.tms.fixture.strategy.RoundRobinFixtureGenerator;
import com.acme.tms.fixture.strategy.SeededParticipant;
import com.acme.tms.result.strategy.CompetitionResults;
import com.acme.tms.result.strategy.LeaderboardRow;
import com.acme.tms.result.strategy.LeaderboardStrategy;
import com.acme.tms.result.strategy.LeaderboardStrategyFactory;
import com.acme.tms.result.strategy.LeaderboardStrategyKey;
import com.acme.tms.result.strategy.LowestTimeLeaderboard;
import com.acme.tms.result.strategy.PointsTableLeaderboard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The extensibility claim in ARCHITECTURE_BRIEF section 7, tested rather than asserted: a sport
 * nobody has written yet must reach production as one new class per strategy and one enum constant,
 * with no edit to a factory, a service or a controller.
 */
class StrategyRegistryTest {

    /**
     * Stands in for Chess — the post-MVP proof in doc 06 section 3.3. It pairs adjacent entrants,
     * which is not real Swiss pairing; what is under test is that a strategy the core has never
     * heard of is dispatched to and its output used.
     */
    private static final class FakeSwissGenerator implements FixtureGenerator {

        @Override
        public FixtureGeneratorKey key() {
            return FixtureGeneratorKey.SWISS;
        }

        @Override
        public boolean supports(ParticipantType participantType) {
            return participantType == ParticipantType.INDIVIDUAL;
        }

        @Override
        public Set<String> requiredRuleKeys() {
            return Set.of("rounds");
        }

        @Override
        public FixturePlan generate(FixtureGenerationContext context) {
            List<SeededParticipant> entrants = context.participants();
            List<FixturePlan.PlannedMatch> matches = new ArrayList<>();

            for (int index = 0; index + 1 < entrants.size(); index += 2) {
                matches.add(new FixturePlan.PlannedMatch(List.of(
                    new FixturePlan.PlannedSlot(entrants.get(index).participantId(), "WHITE", null),
                    new FixturePlan.PlannedSlot(entrants.get(index + 1).participantId(), "BLACK", null)
                )));
            }

            return new FixturePlan(List.of(new FixturePlan.PlannedRound(1, "Round 1", matches)));
        }
    }

    /** The ranking half of the same claim: Buchholz arrives as a class, not as a branch. */
    private static final class FakeBuchholzLeaderboard implements LeaderboardStrategy {

        @Override
        public LeaderboardStrategyKey key() {
            return LeaderboardStrategyKey.BRACKET;
        }

        @Override
        public Set<String> compatibleFixtureGenerators() {
            return Set.of("SWISS");
        }

        @Override
        public List<LeaderboardRow> rank(CompetitionResults results, SportRules rules) {
            List<LeaderboardRow> rows = new ArrayList<>();
            List<UUID> participantIds = results.participantIds();
            for (int index = 0; index < participantIds.size(); index++) {
                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("buchholz", index);
                rows.add(new LeaderboardRow(participantIds.get(index), index + 1, metrics));
            }
            return rows;
        }
    }

    @Test
    void registersOnlyTheStrategiesThatAreActuallyDeployed() {
        FixtureGeneratorFactory factory = new FixtureGeneratorFactory(
            List.of(new RoundRobinFixtureGenerator(), new NoneFixtureGenerator()));

        assertThat(factory.registeredKeys())
            .containsExactlyInAnyOrder(FixtureGeneratorKey.ROUND_ROBIN, FixtureGeneratorKey.NONE);
    }

    @Test
    void anUndeployedKeyIsRejectedRatherThanReturningNull() {
        FixtureGeneratorFactory factory = new FixtureGeneratorFactory(List.of(new NoneFixtureGenerator()));

        assertThatThrownBy(() -> factory.get(FixtureGeneratorKey.ROUND_ROBIN))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("ROUND_ROBIN");
    }

    @Test
    void aNewStrategyDispatchesWithoutTouchingTheFactory() {
        // Adding a format is one class plus one enum constant. If this ever needs a change to
        // FixtureGeneratorFactory, the extensibility claim in doc 06 has been broken.
        FixtureGeneratorFactory factory = new FixtureGeneratorFactory(
            List.of(new RoundRobinFixtureGenerator(), new NoneFixtureGenerator(), new FakeSwissGenerator()));

        FixtureGenerator resolved = factory.get(FixtureGeneratorKey.SWISS);

        assertThat(resolved).isInstanceOf(FakeSwissGenerator.class);
        assertThat(resolved.supports(ParticipantType.INDIVIDUAL)).isTrue();
        assertThat(resolved.supports(ParticipantType.TEAM)).isFalse();
    }

    @Test
    void anUnknownSportGeneratesFixturesThroughTheSameCallPath() {
        FixtureGeneratorFactory factory = new FixtureGeneratorFactory(
            List.of(new RoundRobinFixtureGenerator(), new NoneFixtureGenerator(), new FakeSwissGenerator()));

        List<SeededParticipant> entrants = List.of(
            SeededParticipant.unseeded(UUID.randomUUID(), "Anand"),
            SeededParticipant.unseeded(UUID.randomUUID(), "Carlsen"),
            SeededParticipant.unseeded(UUID.randomUUID(), "Gukesh"),
            SeededParticipant.unseeded(UUID.randomUUID(), "Ju")
        );

        FixturePlan plan = factory.get(FixtureGeneratorKey.SWISS)
            .generate(new FixtureGenerationContext(UUID.randomUUID(), entrants, SportRules.empty()));

        assertThat(plan.matchCount()).isEqualTo(2);
        assertThat(plan.rounds().get(0).matches().get(0).slots())
            .extracting(FixturePlan.PlannedSlot::slot)
            .containsExactly("WHITE", "BLACK");
    }

    @Test
    void aNewStrategyDeclaresItsOwnRuleKeysWithoutTheValidatorKnowingThem() {
        // Config validation asks the strategy what it needs; it holds no per-sport list of its own.
        FixtureGeneratorFactory factory = new FixtureGeneratorFactory(List.of(new FakeSwissGenerator()));

        assertThat(factory.get(FixtureGeneratorKey.SWISS).requiredRuleKeys()).containsExactly("rounds");
    }

    @Test
    void aNewLeaderboardDispatchesTheSameWay() {
        LeaderboardStrategyFactory factory = new LeaderboardStrategyFactory(
            List.of(new PointsTableLeaderboard(), new LowestTimeLeaderboard(), new FakeBuchholzLeaderboard()));

        UUID player = UUID.randomUUID();
        List<LeaderboardRow> rows = factory.get(LeaderboardStrategyKey.BRACKET)
            .rank(new CompetitionResults(List.of(player), List.of()), SportRules.empty());

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.participantId()).isEqualTo(player);
            assertThat(row.metrics()).containsKey("buchholz");
        });
    }

    @Test
    void twoStrategiesClaimingOneKeyFailAtStartup() {
        // Silent last-one-wins would make dispatch depend on bean ordering.
        assertThatThrownBy(() -> new FixtureGeneratorFactory(
            List.of(new NoneFixtureGenerator(), new NoneFixtureGenerator())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate FixtureGenerator");
    }
}
