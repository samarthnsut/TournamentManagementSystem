package com.acme.tms.fixture;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;
import com.acme.tms.support.CompetitionFixture;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drawing a competition and everything that draw is then allowed to do: the DoD's five-team league,
 * the gates on when a draw may be made or rebuilt, and the match lifecycle around it.
 */
class FixtureLifecycleIntegrationTest extends AbstractIntegrationTest {

    private ApiClient.Response generate(CompetitionFixture.Entered entered) {
        return api.post("/api/v1/competitions/" + entered.competitionId() + "/fixtures/generate",
            Map.of(), entered.token());
    }

    private List<JsonNode> matchesOf(JsonNode fixtureSet) {
        List<JsonNode> matches = new ArrayList<>();
        fixtureSet.path("fixtures").forEach(round -> round.path("matches").forEach(matches::add));
        return matches;
    }

    @Test
    void fiveTeamsProduceTenRoundRobinMatches() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "draw@example.com", 5);

        ApiClient.Response response = generate(entered);

        assertThat(response.status()).isEqualTo(201);
        JsonNode body = response.json();
        assertThat(body.path("generatorKey").asText()).isEqualTo("ROUND_ROBIN");
        assertThat(body.path("rounds").asInt()).isEqualTo(5);
        assertThat(body.path("matchCount").asInt()).isEqualTo(10);

        List<JsonNode> matches = matchesOf(body);
        assertThat(matches).hasSize(10);
        assertThat(matches).allSatisfy(match -> {
            assertThat(match.path("status").asText()).isEqualTo("SCHEDULED");
            assertThat(match.path("participants")).hasSize(2);
        });
    }

    @Test
    void everyTeamMeetsEveryOtherTeamExactlyOnce() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "pairs@example.com", 5);

        Set<Set<String>> meetings = new HashSet<>();
        for (JsonNode match : matchesOf(generate(entered).json())) {
            Set<String> pair = new HashSet<>();
            match.path("participants").forEach(participant ->
                pair.add(participant.path("participantId").asText()));
            assertThat(meetings.add(pair)).as("no pair is drawn twice").isTrue();
        }

        assertThat(meetings).hasSize(10);
    }

    @Test
    void participantsCarryTheirNameAndSlotOntoTheFixture() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "slots@example.com", 4);

        JsonNode match = matchesOf(generate(entered).json()).get(0);

        assertThat(match.path("participants").get(0).path("slot").asText()).isEqualTo("HOME");
        assertThat(match.path("participants").get(1).path("slot").asText()).isEqualTo("AWAY");
        assertThat(match.path("participants").get(0).path("name").asText()).startsWith("Team ");
    }

    @Test
    void anOpenCompetitionCannotBeDrawnYet() {
        // BR-F-2: entries have to be settled first, or half the field would be drawn.
        CompetitionFixture fixture = CompetitionFixture.openTeamCompetition(api, "open@example.com");

        ApiClient.Response response = api.post(
            "/api/v1/competitions/" + fixture.competitionId() + "/fixtures/generate",
            Map.of(), fixture.organizerToken());

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("COMPETITION_NOT_CLOSED");
    }

    @Test
    void aFieldTooSmallToPairIsRejected() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "lonely@example.com", 1);

        ApiClient.Response response = generate(entered);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("INSUFFICIENT_PARTICIPANTS");
    }

    @Test
    void drawingTwiceIsRefusedInFavourOfRegenerating() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "twice@example.com", 4);
        generate(entered);

        ApiClient.Response second = generate(entered);

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.errorCode()).isEqualTo("FIXTURE_ALREADY_EXISTS");
    }

    @Test
    void regeneratingReplacesTheDrawWhileNothingHasBeenPlayed() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "redraw@example.com", 4);
        List<JsonNode> before = matchesOf(generate(entered).json());

        ApiClient.Response response = api.post(
            "/api/v1/competitions/" + entered.competitionId() + "/fixtures/regenerate",
            Map.of("confirm", true), entered.token());

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json().path("matchCount").asInt()).isEqualTo(6);

        List<JsonNode> after = matchesOf(api.get(
            "/api/v1/competitions/" + entered.competitionId() + "/fixtures", entered.token()).json());
        assertThat(after).hasSize(6);

        Set<String> oldIds = new HashSet<>();
        before.forEach(match -> oldIds.add(match.path("id").asText()));
        assertThat(after).noneMatch(match -> oldIds.contains(match.path("id").asText()));
    }

    @Test
    void regeneratingWithoutConfirmationIsRejected() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "unconfirmed@example.com", 4);
        generate(entered);

        ApiClient.Response response = api.post(
            "/api/v1/competitions/" + entered.competitionId() + "/fixtures/regenerate",
            Map.of("confirm", false), entered.token());

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void aDrawWithAPlayedMatchCanNoLongerBeRebuilt() {
        // BR-F-3: rebuilding would orphan a result people watched happen.
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "played@example.com", 4);
        JsonNode match = matchesOf(generate(entered).json()).get(0);

        recordWin(entered, match);

        ApiClient.Response response = api.post(
            "/api/v1/competitions/" + entered.competitionId() + "/fixtures/regenerate",
            Map.of("confirm", true), entered.token());

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("MATCHES_HAVE_RESULTS");
    }

    @Test
    void aLiveMatchAlsoBlocksRegeneration() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "live@example.com", 4);
        JsonNode match = matchesOf(generate(entered).json()).get(0);

        api.post("/api/v1/matches/" + match.path("id").asText() + "/start", Map.of(), entered.token());

        ApiClient.Response response = api.post(
            "/api/v1/competitions/" + entered.competitionId() + "/fixtures/regenerate",
            Map.of("confirm", true), entered.token());

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("MATCHES_HAVE_RESULTS");
    }

    @Test
    void aPostponedMatchStillAllowsTheDrawToBeRebuilt() {
        // Postponing is how an organizer parks a match; it must not freeze the whole draw.
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "postponed@example.com", 4);
        JsonNode match = matchesOf(generate(entered).json()).get(0);

        ApiClient.Response postponed = api.post(
            "/api/v1/matches/" + match.path("id").asText() + "/postpone", Map.of(), entered.token());
        assertThat(postponed.status()).isEqualTo(200);
        assertThat(postponed.json().path("status").asText()).isEqualTo("POSTPONED");

        ApiClient.Response response = api.post(
            "/api/v1/competitions/" + entered.competitionId() + "/fixtures/regenerate",
            Map.of("confirm", true), entered.token());

        assertThat(response.status()).isEqualTo(201);
    }

    @Test
    void aPostponedMatchCanBeRescheduledBackIntoTheCalendar() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "reschedule@example.com", 4);
        String matchId = matchesOf(generate(entered).json()).get(0).path("id").asText();

        api.post("/api/v1/matches/" + matchId + "/postpone", Map.of(), entered.token());

        ApiClient.Response response = api.post("/api/v1/matches/" + matchId + "/schedule",
            Map.of("scheduledAt", "2027-02-03T09:30:00Z"), entered.token());

        assertThat(response.status()).isEqualTo(200);
        JsonNode match = response.json().path("match");
        assertThat(match.path("status").asText()).isEqualTo("SCHEDULED");
        assertThat(match.path("scheduledAt").asText()).startsWith("2027-02-03T09:30");
    }

    @Test
    void aCancelledMatchIsTerminal() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "cancelled@example.com", 4);
        String matchId = matchesOf(generate(entered).json()).get(0).path("id").asText();

        api.post("/api/v1/matches/" + matchId + "/cancel", Map.of(), entered.token());

        ApiClient.Response response = api.post("/api/v1/matches/" + matchId + "/start",
            Map.of(), entered.token());

        assertThat(response.status()).isEqualTo(409);
    }

    @Test
    void readingTheDrawBeforeItExistsIsANotFound() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "nodraw@example.com", 4);

        ApiClient.Response response = api.get(
            "/api/v1/competitions/" + entered.competitionId() + "/fixtures", entered.token());

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.errorCode()).isEqualTo("FIXTURE_NOT_FOUND");
    }

    @Test
    void eightRunnersBecomeASingleFinalWithEightLanes() {
        // The NONE generator still produces a shell, so result code never special-cases it.
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedIndividualCompetition(api, "sprint@example.com", 8);

        JsonNode body = generate(entered).json();

        assertThat(body.path("generatorKey").asText()).isEqualTo("NONE");
        assertThat(body.path("matchCount").asInt()).isEqualTo(1);
        assertThat(body.path("fixtures").get(0).path("roundName").asText()).isEqualTo("Final");

        JsonNode race = matchesOf(body).get(0);
        assertThat(race.path("participants")).hasSize(8);
        assertThat(race.path("participants").get(0).path("slot").asText()).isEqualTo("LANE_1");
        assertThat(race.path("participants").get(7).path("slot").asText()).isEqualTo("LANE_8");
    }

    private void recordWin(CompetitionFixture.Entered entered, JsonNode match) {
        List<JsonNode> participants = new ArrayList<>();
        match.path("participants").forEach(participants::add);

        api.post("/api/v1/matches/" + match.path("id").asText() + "/result", Map.of(
            "outcome", "COMPLETED",
            "scores", List.of(
                Map.of("participantId", participants.get(0).path("participantId").asText(), "value", 2),
                Map.of("participantId", participants.get(1).path("participantId").asText(), "value", 1)
            )
        ), entered.token());
    }
}
