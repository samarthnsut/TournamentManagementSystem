package com.acme.tms.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Getting to an OPEN competition takes a tenant, a sport configuration, a tournament, a
 * competition and four lifecycle transitions. Registration tests care about none of that, so it
 * lives here rather than being repeated in every test class.
 */
public record CompetitionFixture(
    String organizerToken,
    UUID organizationUnitId,
    UUID tournamentId,
    UUID competitionId
) {

    /** A competition for INDIVIDUAL entrants (Athletics), already OPEN for registrations. */
    public static CompetitionFixture openIndividualCompetition(ApiClient api, String email) {
        return open(api, email, "ATHLETICS_100M", individualConfig(), null);
    }

    /** A competition for TEAM entrants (Football), already OPEN for registrations. */
    public static CompetitionFixture openTeamCompetition(ApiClient api, String email) {
        return open(api, email, "FOOTBALL", teamConfig(), null);
    }

    /** As {@link #openTeamCompetition} but capped, for testing the maxRegistrations guard. */
    public static CompetitionFixture openTeamCompetition(ApiClient api, String email, int maxRegistrations) {
        return open(api, email, "FOOTBALL", teamConfig(), maxRegistrations);
    }

    private static CompetitionFixture open(
        ApiClient api,
        String email,
        String sportCode,
        Map<String, Object> config,
        Integer maxRegistrations
    ) {
        ApiClient.Session session = api.registerTenant(email, "Registration Test Federation");
        String token = session.accessToken();

        UUID organizationUnitId = UUID.fromString(
            api.get("/api/v1/organization-units", token).json().get(0).path("id").asText());

        UUID sportId = null;
        for (JsonNode sport : api.get("/api/v1/sports", token).json()) {
            if (sport.path("code").asText().equals(sportCode)) {
                sportId = UUID.fromString(sport.path("id").asText());
            }
        }

        UUID configurationId = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitId,
            "sportId", sportId,
            "config", config
        ), token).id();

        UUID tournamentId = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId,
            "name", "Registration Test Games"
        ), token).id();

        Map<String, Object> competitionRequest = new LinkedHashMap<>();
        competitionRequest.put("name", sportCode + " Open");
        competitionRequest.put("sportConfigurationId", configurationId);
        if (maxRegistrations != null) {
            competitionRequest.put("maxRegistrations", maxRegistrations);
        }
        UUID competitionId =
            api.post("/api/v1/tournaments/" + tournamentId + "/competitions", competitionRequest, token).id();

        // A competition may only open once its tournament is published.
        api.post("/api/v1/tournaments/" + tournamentId + "/publish", Map.of(), token);
        api.post("/api/v1/competitions/" + competitionId + "/open", Map.of(), token);

        return new CompetitionFixture(token, organizationUnitId, tournamentId, competitionId);
    }

    private static Map<String, Object> individualConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sport", "ATHLETICS_100M");
        config.put("participantType", "INDIVIDUAL");
        config.put("fixtureGenerator", "NONE");
        config.put("resultEvaluator", "TIME");
        config.put("leaderboardStrategy", "LOWEST_TIME");
        config.put("rules", Map.of("timeUnit", "SECONDS", "precision", 3));
        return config;
    }

    private static Map<String, Object> teamConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sport", "FOOTBALL");
        config.put("participantType", "TEAM");
        config.put("fixtureGenerator", "ROUND_ROBIN");
        config.put("resultEvaluator", "POINTS");
        config.put("leaderboardStrategy", "POINTS_TABLE");
        config.put("rules", Map.of(
            "pointsForWin", 3, "pointsForDraw", 1, "pointsForLoss", 0, "legs", 1,
            "teamSize", Map.of("min", 2, "max", 18)
        ));
        return config;
    }
}
