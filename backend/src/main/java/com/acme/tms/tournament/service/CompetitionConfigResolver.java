package com.acme.tms.tournament.service;

import com.acme.tms.common.domain.ParticipantType;
import com.acme.tms.common.domain.SportRules;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.fixture.strategy.FixtureGeneratorKey;
import com.acme.tms.result.strategy.LeaderboardStrategyKey;
import com.acme.tms.result.strategy.ResultEvaluatorKey;
import com.acme.tms.tournament.domain.Competition;
import com.acme.tms.tournament.domain.SportConfiguration;
import com.acme.tms.tournament.repository.SportConfigurationRepository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The one place that turns a competition into "which strategies run it, on what rules".
 *
 * <p>Fixtures, results and leaderboards all need the same answer, and every one of them would
 * otherwise have to know how a competition reaches its SportConfiguration. Routing it through here
 * is also what keeps the sport code out of their reach — callers get keys and rules, never a sport
 * name to branch on.
 */
@Service
public class CompetitionConfigResolver {

    private final SportConfigurationRepository sportConfigurationRepository;
    private final SportConfigurationValidator sportConfigurationValidator;

    public CompetitionConfigResolver(
        SportConfigurationRepository sportConfigurationRepository,
        SportConfigurationValidator sportConfigurationValidator
    ) {
        this.sportConfigurationRepository = sportConfigurationRepository;
        this.sportConfigurationValidator = sportConfigurationValidator;
    }

    @Transactional(readOnly = true)
    public ResolvedConfig resolve(Competition competition) {
        SportConfiguration configuration = sportConfigurationRepository
            .findByIdAndDeletedAtIsNull(competition.getSportConfigurationId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "SPORT_CONFIGURATION_NOT_FOUND",
                "The competition's sport configuration is missing."
            ));

        JsonNode config = sportConfigurationValidator.parse(configuration.getConfig());

        return new ResolvedConfig(
            configuration.getId(),
            ParticipantType.valueOf(config.get("participantType").asText()),
            FixtureGeneratorKey.valueOf(config.get("fixtureGenerator").asText()),
            ResultEvaluatorKey.valueOf(config.get("resultEvaluator").asText()),
            LeaderboardStrategyKey.valueOf(config.get("leaderboardStrategy").asText()),
            SportRules.of(config.get("rules"))
        );
    }

    public record ResolvedConfig(
        UUID sportConfigurationId,
        ParticipantType participantType,
        FixtureGeneratorKey fixtureGenerator,
        ResultEvaluatorKey resultEvaluator,
        LeaderboardStrategyKey leaderboardStrategy,
        SportRules rules
    ) {
    }
}
