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

class TournamentLifecycleIntegrationTest extends AbstractIntegrationTest {

    private ApiClient.Session session;
    private UUID organizationUnitId;
    private UUID footballConfigId;

    @BeforeEach
    void setUpTenant() {
        session = api.registerTenant("organizer@example.com", "Haryana State Association");
        organizationUnitId = UUID.fromString(
            api.get("/api/v1/organization-units", session.accessToken()).json().get(0).path("id").asText());

        UUID footballSportId = null;
        for (JsonNode sport : api.get("/api/v1/sports", session.accessToken()).json()) {
            if (sport.path("code").asText().equals("FOOTBALL")) {
                footballSportId = UUID.fromString(sport.path("id").asText());
            }
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sport", "FOOTBALL");
        config.put("participantType", "TEAM");
        config.put("fixtureGenerator", "ROUND_ROBIN");
        config.put("resultEvaluator", "POINTS");
        config.put("leaderboardStrategy", "POINTS_TABLE");
        config.put("rules", Map.of("pointsForWin", 3, "pointsForDraw", 1, "pointsForLoss", 0, "legs", 1));

        footballConfigId = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitId,
            "sportId", footballSportId,
            "config", config
        ), session.accessToken()).id();
    }

    private UUID createTournament(String name, String slug) {
        return api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId,
            "name", name,
            "slug", slug,
            "startDate", "2027-02-01",
            "endDate", "2027-02-14"
        ), session.accessToken()).id();
    }

    private UUID addCompetition(UUID tournamentId, String name) {
        return api.post("/api/v1/tournaments/" + tournamentId + "/competitions", Map.of(
            "name", name,
            "sportConfigurationId", footballConfigId,
            "maxRegistrations", 16
        ), session.accessToken()).id();
    }

    @Test
    void walksTheFullLifecycleFromDraftToArchived() {
        UUID tournamentId = createTournament("Haryana Games 2027", "haryana-games-2027");
        UUID competitionId = addCompetition(tournamentId, "Football U16");

        assertThat(api.post("/api/v1/tournaments/" + tournamentId + "/publish", Map.of(), session.accessToken())
            .json().path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(api.post("/api/v1/tournaments/" + tournamentId + "/open-registration", Map.of(), session.accessToken())
            .json().path("status").asText()).isEqualTo("REGISTRATION_OPEN");
        assertThat(api.post("/api/v1/tournaments/" + tournamentId + "/close-registration", Map.of(), session.accessToken())
            .json().path("status").asText()).isEqualTo("REGISTRATION_CLOSED");
        assertThat(api.post("/api/v1/tournaments/" + tournamentId + "/start", Map.of(), session.accessToken())
            .json().path("status").asText()).isEqualTo("IN_PROGRESS");

        // The tournament cannot finish while a competition is still live (BR-T-3).
        ApiClient.Response premature =
            api.post("/api/v1/tournaments/" + tournamentId + "/complete", Map.of(), session.accessToken());
        assertThat(premature.status()).isEqualTo(409);
        assertThat(premature.errorCode()).isEqualTo("COMPETITIONS_NOT_FINISHED");

        api.post("/api/v1/competitions/" + competitionId + "/open", Map.of(), session.accessToken());
        api.post("/api/v1/competitions/" + competitionId + "/close", Map.of(), session.accessToken());
        api.post("/api/v1/competitions/" + competitionId + "/start", Map.of(), session.accessToken());
        api.post("/api/v1/competitions/" + competitionId + "/complete", Map.of(), session.accessToken());

        assertThat(api.post("/api/v1/tournaments/" + tournamentId + "/complete", Map.of(), session.accessToken())
            .json().path("status").asText()).isEqualTo("COMPLETED");
        assertThat(api.post("/api/v1/tournaments/" + tournamentId + "/archive", Map.of(), session.accessToken())
            .json().path("status").asText()).isEqualTo("ARCHIVED");
    }

    @Test
    void rejectsATransitionThatSkipsStates() {
        UUID tournamentId = createTournament("Skip Ahead", "skip-ahead-2027");

        ApiClient.Response response =
            api.post("/api/v1/tournaments/" + tournamentId + "/start", Map.of(), session.accessToken());

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("INVALID_STATE_TRANSITION");
        assertThat(response.json().path("detail").asText()).contains("from DRAFT to IN_PROGRESS");
    }

    @Test
    void refusesToOpenRegistrationWithoutACompetition() {
        UUID tournamentId = createTournament("Empty Games", "empty-games-2027");
        api.post("/api/v1/tournaments/" + tournamentId + "/publish", Map.of(), session.accessToken());

        ApiClient.Response response =
            api.post("/api/v1/tournaments/" + tournamentId + "/open-registration", Map.of(), session.accessToken());

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("NO_COMPETITIONS");
    }

    @Test
    void cancellingATournamentCancelsItsLiveCompetitions() {
        UUID tournamentId = createTournament("Called Off", "called-off-2027");
        UUID competitionId = addCompetition(tournamentId, "Football U16");

        api.post("/api/v1/tournaments/" + tournamentId + "/cancel",
            Map.of("reason", "Venue withdrawn"), session.accessToken());

        assertThat(api.get("/api/v1/competitions/" + competitionId, session.accessToken())
            .json().path("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void slugIsFrozenOncePublishedButEditableWhileDraft() {
        UUID tournamentId = createTournament("Rename Me", "rename-me-2027");

        ApiClient.Response renamed = api.patch("/api/v1/tournaments/" + tournamentId,
            Map.of("slug", "renamed-2027"), session.accessToken());
        assertThat(renamed.status()).isEqualTo(200);
        assertThat(renamed.json().path("slug").asText()).isEqualTo("renamed-2027");

        api.post("/api/v1/tournaments/" + tournamentId + "/publish", Map.of(), session.accessToken());

        ApiClient.Response afterPublish = api.patch("/api/v1/tournaments/" + tournamentId,
            Map.of("slug", "too-late-2027"), session.accessToken());
        assertThat(afterPublish.status()).isEqualTo(409);
        assertThat(afterPublish.errorCode()).isEqualTo("SLUG_IMMUTABLE");
    }

    @Test
    void slugsAreUniqueAcrossThePlatform() {
        createTournament("First", "clashing-slug-2027");

        ApiClient.Response duplicate = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId,
            "name", "Second",
            "slug", "clashing-slug-2027"
        ), session.accessToken());

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.errorCode()).isEqualTo("SLUG_TAKEN");
    }

    @Test
    void generatesASlugWhenNoneIsGiven() {
        ApiClient.Response created = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId,
            "name", "Khelo India Youth Games 2027"
        ), session.accessToken());

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().path("slug").asText()).isEqualTo("khelo-india-youth-games-2027");
    }

    @Test
    void rejectsAnEndDateBeforeTheStartDate() {
        ApiClient.Response response = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId,
            "name", "Backwards",
            "startDate", "2027-02-14",
            "endDate", "2027-02-01"
        ), session.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void onlyDraftTournamentsCanBeDeleted() {
        UUID tournamentId = createTournament("Delete Me", "delete-me-2027");
        assertThat(api.delete("/api/v1/tournaments/" + tournamentId, session.accessToken()).status()).isEqualTo(204);

        UUID published = createTournament("Keep Me", "keep-me-2027");
        api.post("/api/v1/tournaments/" + published + "/publish", Map.of(), session.accessToken());

        ApiClient.Response response = api.delete("/api/v1/tournaments/" + published, session.accessToken());
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("TOURNAMENT_NOT_DELETABLE");
    }

    @Test
    void competitionInheritsOwnershipAndParticipantTypeFromItsConfiguration() {
        UUID tournamentId = createTournament("Ownership", "ownership-2027");
        UUID competitionId = addCompetition(tournamentId, "Football U16");

        JsonNode competition = api.get("/api/v1/competitions/" + competitionId, session.accessToken()).json();

        assertThat(competition.path("organizationUnitId").asText()).isEqualTo(organizationUnitId.toString());
        assertThat(competition.path("participantType").asText()).isEqualTo("TEAM");
        assertThat(competition.path("sportCode").asText()).isEqualTo("FOOTBALL");
    }

    @Test
    void rejectsACompetitionWhoseParticipantTypeContradictsItsConfiguration() {
        UUID tournamentId = createTournament("Mismatch", "mismatch-2027");

        ApiClient.Response response = api.post("/api/v1/tournaments/" + tournamentId + "/competitions", Map.of(
            "name", "Football U16",
            "sportConfigurationId", footballConfigId,
            "participantType", "INDIVIDUAL"
        ), session.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("PARTICIPANT_TYPE_MISMATCH");
    }

    @Test
    void aCompetitionCannotOpenWhileItsTournamentIsStillDraft() {
        UUID tournamentId = createTournament("Unpublished", "unpublished-2027");
        UUID competitionId = addCompetition(tournamentId, "Football U16");

        ApiClient.Response response =
            api.post("/api/v1/competitions/" + competitionId + "/open", Map.of(), session.accessToken());

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("TOURNAMENT_NOT_PUBLISHED");
    }

    @Test
    void aConfigurationInUseCanNoLongerBeChanged() {
        UUID tournamentId = createTournament("In Use", "in-use-2027");
        addCompetition(tournamentId, "Football U16");

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sport", "FOOTBALL");
        config.put("participantType", "TEAM");
        config.put("fixtureGenerator", "ROUND_ROBIN");
        config.put("resultEvaluator", "POINTS");
        config.put("leaderboardStrategy", "POINTS_TABLE");
        config.put("rules", Map.of("pointsForWin", 2, "pointsForDraw", 1, "pointsForLoss", 0, "legs", 2));

        ApiClient.Response response =
            api.put("/api/v1/sport-configurations/" + footballConfigId, config, session.accessToken());

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("CONFIG_IN_USE");
    }
}
