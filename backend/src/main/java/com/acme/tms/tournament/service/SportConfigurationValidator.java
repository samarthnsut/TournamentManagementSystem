package com.acme.tms.tournament.service;

import com.acme.tms.common.domain.ParticipantType;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.fixture.strategy.FixtureGenerator;
import com.acme.tms.fixture.strategy.FixtureGeneratorFactory;
import com.acme.tms.fixture.strategy.FixtureGeneratorKey;
import com.acme.tms.result.strategy.LeaderboardStrategy;
import com.acme.tms.result.strategy.LeaderboardStrategyFactory;
import com.acme.tms.result.strategy.LeaderboardStrategyKey;
import com.acme.tms.result.strategy.ResultEvaluator;
import com.acme.tms.result.strategy.ResultEvaluatorFactory;
import com.acme.tms.result.strategy.ResultEvaluatorKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The single gate every SportConfiguration passes through, used by both the create and the update
 * path so the two can never drift. Implements the five checks in 06_SPORT_CONFIGURATION_ENGINE
 * section 7: schema, registry, participant type, per-strategy rule keys, and cross-key coherence.
 */
@Service
public class SportConfigurationValidator {

    /** Verbatim from doc 06 section 3. */
    private static final String CONFIG_SCHEMA = """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "https://schemas.acme-tms.dev/sport-configuration/v1",
          "type": "object",
          "required": ["sport", "participantType", "fixtureGenerator",
                       "resultEvaluator", "leaderboardStrategy", "rules"],
          "additionalProperties": false,
          "properties": {
            "sport": { "type": "string", "minLength": 1 },
            "participantType": { "enum": ["INDIVIDUAL", "TEAM", "ORGANIZATION"] },
            "fixtureGenerator": {
              "enum": ["ROUND_ROBIN", "SINGLE_ELIMINATION", "DOUBLE_ELIMINATION", "SWISS", "NONE"]
            },
            "resultEvaluator": {
              "enum": ["POINTS", "WIN_LOSS", "TIME", "DISTANCE", "SCORE"]
            },
            "leaderboardStrategy": {
              "enum": ["POINTS_TABLE", "LOWEST_TIME", "HIGHEST_DISTANCE", "HIGHEST_SCORE", "BRACKET"]
            },
            "rules": { "type": "object" }
          }
        }
        """;

    private final ObjectMapper objectMapper;
    private final JsonSchema schema;
    private final FixtureGeneratorFactory fixtureGeneratorFactory;
    private final ResultEvaluatorFactory resultEvaluatorFactory;
    private final LeaderboardStrategyFactory leaderboardStrategyFactory;

    public SportConfigurationValidator(
        ObjectMapper objectMapper,
        FixtureGeneratorFactory fixtureGeneratorFactory,
        ResultEvaluatorFactory resultEvaluatorFactory,
        LeaderboardStrategyFactory leaderboardStrategyFactory
    ) {
        this.objectMapper = objectMapper;
        this.fixtureGeneratorFactory = fixtureGeneratorFactory;
        this.resultEvaluatorFactory = resultEvaluatorFactory;
        this.leaderboardStrategyFactory = leaderboardStrategyFactory;
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(CONFIG_SCHEMA);
    }

    /**
     * @return the parsed, validated configuration
     * @throws ValidationException with every problem found, so a caller fixing a config sees the
     *     whole list rather than discovering faults one round trip at a time
     */
    public ValidatedConfig validate(JsonNode config) {
        if (config == null || !config.isObject()) {
            throw new ValidationException("INVALID_SPORT_CONFIGURATION", "config must be a JSON object.");
        }

        // 1. Shape, required keys, enum membership.
        Set<ValidationMessage> schemaErrors = schema.validate(config);
        if (!schemaErrors.isEmpty()) {
            Set<String> messages = new TreeSet<>();
            schemaErrors.forEach(error -> messages.add(error.getMessage()));
            throw new ValidationException(
                "INVALID_SPORT_CONFIGURATION",
                "Sport configuration failed schema validation: " + String.join("; ", messages)
            );
        }

        // 2. Registry check. The schema allows every frozen key, but only deployed strategies work.
        FixtureGeneratorKey fixtureKey = FixtureGeneratorKey.valueOf(config.get("fixtureGenerator").asText());
        ResultEvaluatorKey evaluatorKey = ResultEvaluatorKey.valueOf(config.get("resultEvaluator").asText());
        LeaderboardStrategyKey leaderboardKey =
            LeaderboardStrategyKey.valueOf(config.get("leaderboardStrategy").asText());

        List<String> problems = new ArrayList<>();
        if (!fixtureGeneratorFactory.registeredKeys().contains(fixtureKey)) {
            problems.add("fixtureGenerator " + fixtureKey + " has no deployed implementation");
        }
        if (!resultEvaluatorFactory.registeredKeys().contains(evaluatorKey)) {
            problems.add("resultEvaluator " + evaluatorKey + " has no deployed implementation");
        }
        if (!leaderboardStrategyFactory.registeredKeys().contains(leaderboardKey)) {
            problems.add("leaderboardStrategy " + leaderboardKey + " has no deployed implementation");
        }
        if (!problems.isEmpty()) {
            throw new ValidationException("UNKNOWN_STRATEGY_KEY", String.join("; ", problems));
        }

        FixtureGenerator generator = fixtureGeneratorFactory.get(fixtureKey);
        ResultEvaluator evaluator = resultEvaluatorFactory.get(evaluatorKey);
        LeaderboardStrategy leaderboard = leaderboardStrategyFactory.get(leaderboardKey);

        // 3. The chosen format must accept this kind of participant.
        ParticipantType participantType = ParticipantType.valueOf(config.get("participantType").asText());
        if (!generator.supports(participantType)) {
            problems.add("fixtureGenerator " + fixtureKey + " does not support participantType " + participantType);
        }

        // 4. Each selected strategy declares the rule keys it cannot work without.
        JsonNode rules = config.get("rules");
        Set<String> requiredRuleKeys = new LinkedHashSet<>();
        requiredRuleKeys.addAll(generator.requiredRuleKeys());
        requiredRuleKeys.addAll(evaluator.requiredRuleKeys());
        requiredRuleKeys.addAll(leaderboard.requiredRuleKeys());
        for (String ruleKey : requiredRuleKeys) {
            if (!rules.has(ruleKey)) {
                problems.add("rules." + ruleKey + " is required by the selected strategies");
            }
        }

        // 5. Coherence: a ranking that cannot be computed from the chosen format is nonsense.
        if (!leaderboard.compatibleFixtureGenerators().contains(fixtureKey.name())) {
            problems.add(
                "leaderboardStrategy " + leaderboardKey + " is not compatible with fixtureGenerator " + fixtureKey
            );
        }

        if (!problems.isEmpty()) {
            throw new ValidationException("INVALID_SPORT_CONFIGURATION", String.join("; ", problems));
        }

        return new ValidatedConfig(participantType, fixtureKey, evaluatorKey, leaderboardKey);
    }

    /** Parses a raw JSON string, rejecting malformed input before schema validation. */
    public JsonNode parse(String rawConfig) {
        try {
            return objectMapper.readTree(rawConfig);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ValidationException("INVALID_SPORT_CONFIGURATION", "config is not valid JSON.");
        }
    }

    public record ValidatedConfig(
        ParticipantType participantType,
        FixtureGeneratorKey fixtureGenerator,
        ResultEvaluatorKey resultEvaluator,
        LeaderboardStrategyKey leaderboardStrategy
    ) {
    }
}
