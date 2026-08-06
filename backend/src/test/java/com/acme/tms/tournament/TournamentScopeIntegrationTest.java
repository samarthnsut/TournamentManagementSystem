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
 * Sprint 2 proved organization units are isolated between tenants. Tournaments and competitions are
 * the first entities scoped through a resolver rather than directly, so the same guarantee is
 * re-proven here at the new scope types.
 */
class TournamentScopeIntegrationTest extends AbstractIntegrationTest {

    private record Tenant(ApiClient.Session session, UUID organizationUnitId, UUID configId) {
    }

    private Tenant setUpTenant(String email, String organizationName) {
        ApiClient.Session session = api.registerTenant(email, organizationName);
        UUID organizationUnitId = UUID.fromString(
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

        UUID configId = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitId,
            "sportId", sportId,
            "config", config
        ), session.accessToken()).id();

        return new Tenant(session, organizationUnitId, configId);
    }

    private UUID createTournament(Tenant tenant, String name, String slug) {
        return api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", tenant.organizationUnitId(),
            "name", name,
            "slug", slug
        ), tenant.session().accessToken()).id();
    }

    @Test
    void oneTenantCannotReadAnotherTenantsTournament() {
        Tenant haryana = setUpTenant("haryana@example.com", "Haryana State Association");
        Tenant punjab = setUpTenant("punjab@example.com", "Punjab State Association");

        UUID punjabTournament = createTournament(punjab, "Punjab Games", "punjab-games-2027");

        ApiClient.Response response =
            api.get("/api/v1/tournaments/" + punjabTournament, haryana.session().accessToken());

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.errorCode()).isEqualTo("SCOPE_FORBIDDEN");
    }

    @Test
    void oneTenantCannotTransitionAnotherTenantsTournament() {
        Tenant haryana = setUpTenant("haryana2@example.com", "Haryana State Association");
        Tenant punjab = setUpTenant("punjab2@example.com", "Punjab State Association");

        UUID punjabTournament = createTournament(punjab, "Punjab Games", "punjab-games-b-2027");

        ApiClient.Response response = api.post(
            "/api/v1/tournaments/" + punjabTournament + "/publish", Map.of(), haryana.session().accessToken());

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void listingReturnsOnlyTournamentsTheCallerOwns() {
        Tenant haryana = setUpTenant("haryana3@example.com", "Haryana State Association");
        Tenant punjab = setUpTenant("punjab3@example.com", "Punjab State Association");

        createTournament(haryana, "Haryana Games", "haryana-games-c-2027");
        createTournament(punjab, "Punjab Games", "punjab-games-c-2027");

        JsonNode visible = api.get("/api/v1/tournaments", haryana.session().accessToken()).json();

        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).path("name").asText()).isEqualTo("Haryana Games");
    }

    @Test
    void anOrganizationGrantReachesCompetitionsBeneathIt() {
        // The competition is only ever granted via its owning organization unit, so this proves the
        // ownership resolver widens access correctly rather than the caller holding a direct grant.
        Tenant haryana = setUpTenant("haryana4@example.com", "Haryana State Association");
        UUID tournamentId = createTournament(haryana, "Haryana Games", "haryana-games-d-2027");

        UUID competitionId = api.post("/api/v1/tournaments/" + tournamentId + "/competitions", Map.of(
            "name", "Football U16",
            "sportConfigurationId", haryana.configId()
        ), haryana.session().accessToken()).id();

        ApiClient.Response response =
            api.get("/api/v1/competitions/" + competitionId, haryana.session().accessToken());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().path("name").asText()).isEqualTo("Football U16");
    }

    @Test
    void oneTenantCannotReadAnotherTenantsCompetition() {
        Tenant haryana = setUpTenant("haryana5@example.com", "Haryana State Association");
        Tenant punjab = setUpTenant("punjab5@example.com", "Punjab State Association");

        UUID punjabTournament = createTournament(punjab, "Punjab Games", "punjab-games-e-2027");
        UUID punjabCompetition = api.post("/api/v1/tournaments/" + punjabTournament + "/competitions", Map.of(
            "name", "Football U16",
            "sportConfigurationId", punjab.configId()
        ), punjab.session().accessToken()).id();

        ApiClient.Response response =
            api.get("/api/v1/competitions/" + punjabCompetition, haryana.session().accessToken());

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void creatingATournamentInAnotherTenantsUnitIsRefused() {
        Tenant haryana = setUpTenant("haryana6@example.com", "Haryana State Association");
        Tenant punjab = setUpTenant("punjab6@example.com", "Punjab State Association");

        ApiClient.Response response = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", punjab.organizationUnitId(),
            "name", "Trespassing",
            "slug", "trespassing-2027"
        ), haryana.session().accessToken());

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void tournamentEndpointsRejectAnonymousCallers() {
        assertThat(api.get("/api/v1/tournaments", null).status()).isEqualTo(401);
    }
}
