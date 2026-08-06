package com.acme.tms.tournament;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the promise that adding a sport is configuration rather than code: anything the engine
 * cannot actually dispatch has to be rejected at save time, not at fixture-generation time.
 */
class SportConfigurationIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, Object> footballConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sport", "FOOTBALL");
        config.put("participantType", "TEAM");
        config.put("fixtureGenerator", "ROUND_ROBIN");
        config.put("resultEvaluator", "POINTS");
        config.put("leaderboardStrategy", "POINTS_TABLE");
        config.put("rules", Map.of(
            "pointsForWin", 3, "pointsForDraw", 1, "pointsForLoss", 0, "legs", 1
        ));
        return config;
    }

    private UUID sportId(ApiClient.Session session, String code) {
        JsonNode sports = api.get("/api/v1/sports", session.accessToken()).json();
        for (JsonNode sport : sports) {
            if (sport.path("code").asText().equals(code)) {
                return UUID.fromString(sport.path("id").asText());
            }
        }
        throw new IllegalStateException("Sport " + code + " was not seeded");
    }

    @Test
    void seedsTheTwoMvpSports() {
        ApiClient.Session session = api.registerTenant("sports@example.com", "Sports Federation");

        JsonNode sports = api.get("/api/v1/sports", session.accessToken()).json();

        assertThat(sports).hasSize(2);
        assertThat(sports.findValuesAsText("code")).containsExactlyInAnyOrder("FOOTBALL", "ATHLETICS_100M");
    }

    @Test
    void acceptsAValidFootballConfiguration() {
        ApiClient.Session session = api.registerTenant("football@example.com", "Football Federation");
        UUID organizationUnitId = organizationUnitIdOf(session);

        ApiClient.Response created = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitId,
            "sportId", sportId(session, "FOOTBALL"),
            "config", footballConfig()
        ), session.accessToken());

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().path("config").path("fixtureGenerator").asText()).isEqualTo("ROUND_ROBIN");
        assertThat(created.json().path("version").asInt()).isEqualTo(1);
    }

    @Test
    void rejectsAStrategyKeyOutsideTheFrozenEnum() {
        ApiClient.Session session = api.registerTenant("ladder@example.com", "Ladder Federation");
        Map<String, Object> config = footballConfig();
        config.put("fixtureGenerator", "LADDER");

        ApiClient.Response response = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitIdOf(session),
            "sportId", sportId(session, "FOOTBALL"),
            "config", config
        ), session.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("INVALID_SPORT_CONFIGURATION");
    }

    @Test
    void rejectsAKnownKeyThatHasNoDeployedStrategy() {
        // SWISS is in the frozen enum but ships no implementation until Chess is supported, so a
        // config naming it must fail now rather than at fixture generation.
        ApiClient.Session session = api.registerTenant("swiss@example.com", "Chess Federation");
        Map<String, Object> config = footballConfig();
        config.put("fixtureGenerator", "SWISS");

        ApiClient.Response response = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitIdOf(session),
            "sportId", sportId(session, "FOOTBALL"),
            "config", config
        ), session.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("UNKNOWN_STRATEGY_KEY");
    }

    @Test
    void rejectsAnIncoherentStrategyCombination() {
        // A points table cannot be computed from a competition that generates no pairings.
        ApiClient.Session session = api.registerTenant("incoherent@example.com", "Mixed Federation");
        Map<String, Object> config = footballConfig();
        config.put("fixtureGenerator", "NONE");

        ApiClient.Response response = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitIdOf(session),
            "sportId", sportId(session, "FOOTBALL"),
            "config", config
        ), session.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().path("detail").asText()).contains("not compatible");
    }

    @Test
    void rejectsRulesMissingAKeyTheStrategyNeeds() {
        ApiClient.Session session = api.registerTenant("norules@example.com", "Sparse Federation");
        Map<String, Object> config = footballConfig();
        config.put("rules", Map.of("legs", 1));

        ApiClient.Response response = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitIdOf(session),
            "sportId", sportId(session, "FOOTBALL"),
            "config", config
        ), session.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().path("detail").asText()).contains("pointsForWin");
    }

    @Test
    void rejectsAConfigurationMissingRequiredTopLevelKeys() {
        ApiClient.Session session = api.registerTenant("partial@example.com", "Partial Federation");

        ApiClient.Response response = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitIdOf(session),
            "sportId", sportId(session, "FOOTBALL"),
            "config", Map.of("sport", "FOOTBALL", "participantType", "TEAM")
        ), session.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("INVALID_SPORT_CONFIGURATION");
    }

    @Test
    void acceptsTheAthleticsConfigurationWithItsOwnStrategyTriple() {
        ApiClient.Session session = api.registerTenant("athletics@example.com", "Athletics Federation");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sport", "ATHLETICS_100M");
        config.put("participantType", "INDIVIDUAL");
        config.put("fixtureGenerator", "NONE");
        config.put("resultEvaluator", "TIME");
        config.put("leaderboardStrategy", "LOWEST_TIME");
        config.put("rules", Map.of("timeUnit", "SECONDS", "precision", 3));

        ApiClient.Response created = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitIdOf(session),
            "sportId", sportId(session, "ATHLETICS_100M"),
            "config", config
        ), session.accessToken());

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().path("config").path("leaderboardStrategy").asText()).isEqualTo("LOWEST_TIME");
    }

    private UUID organizationUnitIdOf(ApiClient.Session session) {
        JsonNode units = api.get("/api/v1/organization-units", session.accessToken()).json();
        return UUID.fromString(units.get(0).path("id").asText());
    }
}
