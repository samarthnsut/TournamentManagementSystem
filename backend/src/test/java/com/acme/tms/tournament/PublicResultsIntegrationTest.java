package com.acme.tms.tournament;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;
import com.acme.tms.support.CompetitionFixture;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The anonymous fixtures and standings on {@code /t/{slug}}.
 *
 * <p>Half of these are security tests rather than feature tests: an endpoint that needs no token is
 * one that has to prove it cannot be walked sideways into a tournament nobody published.
 */
class PublicResultsIntegrationTest extends AbstractIntegrationTest {

    private String slugOf(CompetitionFixture.Entered entered) {
        return api.get("/api/v1/tournaments/" + entered.fixture().tournamentId(), entered.token())
            .json()
            .path("slug")
            .asText();
    }

    private List<JsonNode> draw(CompetitionFixture.Entered entered) {
        JsonNode body = api.post("/api/v1/competitions/" + entered.competitionId() + "/fixtures/generate",
            Map.of(), entered.token()).json();
        List<JsonNode> matches = new ArrayList<>();
        body.path("fixtures").forEach(round -> round.path("matches").forEach(matches::add));
        return matches;
    }

    private void recordWin(CompetitionFixture.Entered entered, JsonNode match) {
        List<JsonNode> participants = new ArrayList<>();
        match.path("participants").forEach(participants::add);

        api.post("/api/v1/matches/" + match.path("id").asText() + "/result", Map.of(
            "outcome", "COMPLETED",
            "scores", List.of(
                Map.of("participantId", participants.get(0).path("participantId").asText(), "value", 2),
                Map.of("participantId", participants.get(1).path("participantId").asText(), "value", 0)
            )
        ), entered.token());
    }

    @Test
    void anyoneCanReadTheFixturesOfAPublishedTournament() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "publicfix@example.com", 4);
        draw(entered);

        // No token at all — this is the visitor who has never signed in.
        ApiClient.Response response = api.get(
            "/api/v1/public/t/" + slugOf(entered) + "/competitions/" + entered.competitionId() + "/fixtures",
            null);

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = response.json();
        assertThat(body.path("generatorKey").asText()).isEqualTo("ROUND_ROBIN");
        assertThat(body.path("matchCount").asInt()).isEqualTo(6);
        assertThat(body.path("fixtures")).hasSize(3);
        assertThat(body.path("competitionName").asText()).isEqualTo("FOOTBALL Open");
    }

    @Test
    void thePublicFixtureViewOmitsOrganizerPlumbing() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "narrow@example.com", 4);
        draw(entered);

        JsonNode match = api.get(
            "/api/v1/public/t/" + slugOf(entered) + "/competitions/" + entered.competitionId() + "/fixtures",
            null).json().path("fixtures").get(0).path("matches").get(0);

        // A spectator has no use for these, and the optimistic-lock version is a write concern.
        assertThat(match.has("version")).isFalse();
        assertThat(match.has("venueId")).isFalse();
        assertThat(match.has("fixtureId")).isFalse();
        assertThat(match.path("participants").get(0).has("seed")).isFalse();
        // What they do need survives.
        assertThat(match.path("participants").get(0).path("name").asText()).startsWith("Team ");
        assertThat(match.path("status").asText()).isEqualTo("SCHEDULED");
    }

    @Test
    void anyoneCanReadTheStandingsOfAPublishedTournament() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "publicboard@example.com", 4);
        recordWin(entered, draw(entered).get(0));

        ApiClient.Response response = api.get(
            "/api/v1/public/t/" + slugOf(entered) + "/competitions/" + entered.competitionId() + "/leaderboard",
            null);

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = response.json();
        assertThat(body.path("strategyKey").asText()).isEqualTo("POINTS_TABLE");
        assertThat(body.path("entries")).hasSize(4);
        assertThat(body.path("entries").get(0).path("metrics").path("points").asInt()).isEqualTo(3);
    }

    @Test
    void theAnonymousBoardMatchesTheOneTheOrganizerSees() {
        // Two code paths reading one table is how a public page starts quietly lying.
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "sameboard@example.com", 4);
        recordWin(entered, draw(entered).get(0));

        JsonNode organizerView = api.get(
            "/api/v1/competitions/" + entered.competitionId() + "/leaderboard", entered.token()).json();
        JsonNode publicView = api.get(
            "/api/v1/public/t/" + slugOf(entered) + "/competitions/" + entered.competitionId() + "/leaderboard",
            null).json();

        assertThat(publicView).isEqualTo(organizerView);
    }

    @Test
    void aDraftTournamentIsInvisibleEvenWithTheRightSlugAndCompetitionId() {
        // Both ids are real; the tournament has simply never been published. Knowing the slug must
        // not be a way in, or a draft's existence leaks the moment someone guesses its name.
        ApiClient.Session session = api.registerTenant("draftpublic@example.com", "Draft Federation");
        String token = session.accessToken();
        UUID organizationUnitId = UUID.fromString(
            api.get("/api/v1/organization-units", token).json().get(0).path("id").asText());

        UUID sportId = null;
        for (JsonNode sport : api.get("/api/v1/sports", token).json()) {
            if (sport.path("code").asText().equals("FOOTBALL")) {
                sportId = UUID.fromString(sport.path("id").asText());
            }
        }

        UUID configurationId = api.post("/api/v1/sport-configurations", Map.of(
            "organizationUnitId", organizationUnitId,
            "sportId", sportId,
            "config", Map.of(
                "sport", "FOOTBALL",
                "participantType", "TEAM",
                "fixtureGenerator", "ROUND_ROBIN",
                "resultEvaluator", "POINTS",
                "leaderboardStrategy", "POINTS_TABLE",
                "rules", Map.of("pointsForWin", 3, "pointsForDraw", 1, "pointsForLoss", 0, "legs", 1))
        ), token).id();

        // Created and left in DRAFT — never published.
        UUID tournamentId = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId, "name", "Unannounced Cup"), token).id();
        UUID competitionId = api.post("/api/v1/tournaments/" + tournamentId + "/competitions",
            Map.of("name", "Football U16", "sportConfigurationId", configurationId), token).id();
        String slug = api.get("/api/v1/tournaments/" + tournamentId, token).json().path("slug").asText();

        ApiClient.Response board = api.get(
            "/api/v1/public/t/" + slug + "/competitions/" + competitionId + "/leaderboard", null);
        ApiClient.Response fixtures = api.get(
            "/api/v1/public/t/" + slug + "/competitions/" + competitionId + "/fixtures", null);

        // 404, never 403: a forbidden would confirm the draft exists.
        assertThat(board.status()).isEqualTo(404);
        assertThat(board.errorCode()).isEqualTo("TOURNAMENT_NOT_FOUND");
        assertThat(fixtures.status()).isEqualTo(404);
        assertThat(fixtures.errorCode()).isEqualTo("TOURNAMENT_NOT_FOUND");
    }

    @Test
    void aCompetitionCannotBeReadThroughAnotherTournamentsSlug() {
        CompetitionFixture.Entered mine =
            CompetitionFixture.closedTeamCompetition(api, "mine@example.com", 4);
        recordWin(mine, draw(mine).get(0));

        CompetitionFixture.Entered theirs =
            CompetitionFixture.closedTeamCompetition(api, "theirs@example.com", 4);

        // Their slug, my competition id — the pairing has to be checked, not just the slug.
        ApiClient.Response response = api.get(
            "/api/v1/public/t/" + slugOf(theirs) + "/competitions/" + mine.competitionId() + "/leaderboard",
            null);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.errorCode()).isEqualTo("COMPETITION_NOT_FOUND");
    }

    @Test
    void fixturesAreAlsoPairedAgainstTheSlug() {
        CompetitionFixture.Entered mine =
            CompetitionFixture.closedTeamCompetition(api, "minefix@example.com", 4);
        draw(mine);
        CompetitionFixture.Entered theirs =
            CompetitionFixture.closedTeamCompetition(api, "theirsfix@example.com", 4);

        ApiClient.Response response = api.get(
            "/api/v1/public/t/" + slugOf(theirs) + "/competitions/" + mine.competitionId() + "/fixtures",
            null);

        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void anUnknownSlugIsANotFound() {
        ApiClient.Response response = api.get(
            "/api/v1/public/t/no-such-tournament/competitions/" + UUID.randomUUID() + "/leaderboard", null);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.errorCode()).isEqualTo("TOURNAMENT_NOT_FOUND");
    }

    @Test
    void aCompetitionWithNoDrawAnswersNotFound() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "nodrawpublic@example.com", 4);

        ApiClient.Response response = api.get(
            "/api/v1/public/t/" + slugOf(entered) + "/competitions/" + entered.competitionId() + "/fixtures",
            null);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.errorCode()).isEqualTo("FIXTURE_NOT_FOUND");
    }

    @Test
    void aBoardWithNothingPlayedAnswersTheSameConflictAsThePrivateOne() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "emptypublic@example.com", 4);
        draw(entered);

        ApiClient.Response response = api.get(
            "/api/v1/public/t/" + slugOf(entered) + "/competitions/" + entered.competitionId() + "/leaderboard",
            null);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("LEADERBOARD_NOT_AVAILABLE");
    }

    @Test
    void aTimedEventReadsPubliclyThroughTheSameEndpoints() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedIndividualCompetition(api, "publicsprint@example.com", 4);
        JsonNode race = draw(entered).get(0);

        List<Map<String, Object>> scores = new ArrayList<>();
        double time = 11.0;
        for (JsonNode participant : race.path("participants")) {
            scores.add(Map.of(
                "participantId", participant.path("participantId").asText(),
                "value", time,
                "unit", "SECONDS"));
            time += 0.5;
        }
        api.post("/api/v1/matches/" + race.path("id").asText() + "/result",
            Map.of("outcome", "COMPLETED", "scores", scores), entered.token());

        String slug = slugOf(entered);
        JsonNode board = api.get(
            "/api/v1/public/t/" + slug + "/competitions/" + entered.competitionId() + "/leaderboard", null).json();
        JsonNode fixtures = api.get(
            "/api/v1/public/t/" + slug + "/competitions/" + entered.competitionId() + "/fixtures", null).json();

        assertThat(board.path("strategyKey").asText()).isEqualTo("LOWEST_TIME");
        assertThat(board.path("entries").get(0).path("metrics").path("bestValue").asDouble()).isEqualTo(11.0);
        assertThat(fixtures.path("generatorKey").asText()).isEqualTo("NONE");
        assertThat(fixtures.path("fixtures").get(0).path("roundName").asText()).isEqualTo("Final");
        assertThat(fixtures.path("fixtures").get(0).path("matches").get(0).path("result")
            .path("participants").get(0).path("unit").asText()).isEqualTo("SECONDS");
    }
}
