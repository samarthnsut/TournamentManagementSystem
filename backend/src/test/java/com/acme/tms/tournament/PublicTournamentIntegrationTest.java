package com.acme.tms.tournament;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicTournamentIntegrationTest extends AbstractIntegrationTest {

    private ApiClient.Session session;
    private UUID organizationUnitId;
    private UUID configId;

    @BeforeEach
    void setUpTenant() {
        session = api.registerTenant("public@example.com", "Haryana State Association");
        organizationUnitId = UUID.fromString(
            api.get("/api/v1/organization-units", session.accessToken()).json().get(0).path("id").asText());

        UUID sportId = null;
        for (JsonNode sport : api.get("/api/v1/sports", session.accessToken()).json()) {
            if (sport.path("code").asText().equals("FOOTBALL")) {
                sportId = UUID.fromString(sport.path("id").asText());
            }
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sport", "FOOTBALL");
        config.put("participantType", "TEAM");
        config.put("fixtureGenerator", "ROUND_ROBIN");
        config.put("resultEvaluator", "POINTS");
        config.put("leaderboardStrategy", "POINTS_TABLE");
        config.put("rules", Map.of("pointsForWin", 3, "pointsForDraw", 1, "pointsForLoss", 0, "legs", 1));

        configId = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitId,
            "sportId", sportId,
            "config", config
        ), session.accessToken()).id();
    }

    private UUID publishedTournament(String slug) {
        UUID tournamentId = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId,
            "name", "Haryana Games 2027",
            "slug", slug,
            "description", "State-level multi-sport games.",
            "startDate", "2027-02-01",
            "endDate", "2027-02-14"
        ), session.accessToken()).id();

        api.post("/api/v1/tournaments/" + tournamentId + "/competitions", Map.of(
            "name", "Football U16",
            "sportConfigurationId", configId
        ), session.accessToken());

        api.post("/api/v1/tournaments/" + tournamentId + "/publish", Map.of(), session.accessToken());
        return tournamentId;
    }

    @Test
    void publishedTournamentIsReadableWithoutTheslightestAuthentication() {
        publishedTournament("haryana-games-2027");

        // Deliberately no token.
        ApiClient.Response response = api.get("/api/v1/public/t/haryana-games-2027", null);

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = response.json();
        assertThat(body.path("name").asText()).isEqualTo("Haryana Games 2027");
        assertThat(body.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(body.path("organizer").path("name").asText()).isEqualTo("Haryana State Association");
        assertThat(body.path("competitions")).hasSize(1);
        assertThat(body.path("competitions").get(0).path("sportCode").asText()).isEqualTo("FOOTBALL");
    }

    @Test
    void draftTournamentIsInvisibleToThePublic() {
        api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId,
            "name", "Secret Plans",
            "slug", "secret-plans-2027"
        ), session.accessToken());

        ApiClient.Response response = api.get("/api/v1/public/t/secret-plans-2027", null);

        // 404 rather than 403: a draft's very existence should not be discoverable by probing.
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.errorCode()).isEqualTo("TOURNAMENT_NOT_FOUND");
    }

    @Test
    void unknownSlugIsAPlainNotFound() {
        assertThat(api.get("/api/v1/public/t/no-such-tournament", null).status()).isEqualTo(404);
    }

    @Test
    void publicViewOmitsInternalIdentifiers() {
        publishedTournament("haryana-games-2027");

        JsonNode body = api.get("/api/v1/public/t/haryana-games-2027", null).json();

        assertThat(body.has("organizationUnitId")).isFalse();
        assertThat(body.has("createdAt")).isFalse();
        assertThat(body.has("publishedAt")).isFalse();
    }
}
