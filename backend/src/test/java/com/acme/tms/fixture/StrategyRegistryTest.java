package com.acme.tms.fixture;

import com.acme.tms.common.domain.ParticipantType;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.fixture.strategy.FixtureGenerator;
import com.acme.tms.fixture.strategy.FixtureGeneratorFactory;
import com.acme.tms.fixture.strategy.FixtureGeneratorKey;
import com.acme.tms.fixture.strategy.NoneFixtureGenerator;
import com.acme.tms.fixture.strategy.RoundRobinFixtureGenerator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyRegistryTest {

    /** Stands in for a sport nobody has written yet — the Chess-readiness promise in the brief. */
    private static final class FakeSwissGenerator implements FixtureGenerator {

        @Override
        public FixtureGeneratorKey key() {
            return FixtureGeneratorKey.SWISS;
        }

        @Override
        public boolean supports(ParticipantType participantType) {
            return participantType == ParticipantType.INDIVIDUAL;
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
    void twoStrategiesClaimingOneKeyFailAtStartup() {
        // Silent last-one-wins would make dispatch depend on bean ordering.
        assertThatThrownBy(() -> new FixtureGeneratorFactory(
            List.of(new NoneFixtureGenerator(), new NoneFixtureGenerator())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate FixtureGenerator");
    }
}
