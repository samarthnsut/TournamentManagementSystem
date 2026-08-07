package com.acme.tms.result;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;
import com.acme.tms.support.CompetitionFixture;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Sprint 6 definition of done, end to end: a football league whose table is correct down to the
 * tie-breaker, and a sprint final whose board is ordered by the clock. Both run through the same
 * endpoints with nothing sport-specific between them but their configuration.
 */
class LeaderboardIntegrationTest extends AbstractIntegrationTest {

    private List<JsonNode> drawMatches(CompetitionFixture.Entered entered) {
        JsonNode body = api.post("/api/v1/competitions/" + entered.competitionId() + "/fixtures/generate",
            Map.of(), entered.token()).json();

        List<JsonNode> matches = new ArrayList<>();
        body.path("fixtures").forEach(round -> round.path("matches").forEach(matches::add));
        return matches;
    }

    /** Scores a specific pairing by name, since the draw order is not the test's to control. */
    private ApiClient.Response record(
        CompetitionFixture.Entered entered,
        List<JsonNode> matches,
        String firstName,
        int firstScore,
        String secondName,
        int secondScore
    ) {
        for (JsonNode match : matches) {
            Map<String, String> idsByName = new LinkedHashMap<>();
            match.path("participants").forEach(participant ->
                idsByName.put(participant.path("name").asText(), participant.path("participantId").asText()));

            if (!idsByName.containsKey(firstName) || !idsByName.containsKey(secondName)) {
                continue;
            }

            return api.post("/api/v1/matches/" + match.path("id").asText() + "/result", Map.of(
                "outcome", "COMPLETED",
                "scores", List.of(
                    Map.of("participantId", idsByName.get(firstName), "value", firstScore),
                    Map.of("participantId", idsByName.get(secondName), "value", secondScore)
                )
            ), entered.token());
        }

        throw new AssertionError("No match was drawn between " + firstName + " and " + secondName);
    }

    private JsonNode leaderboard(CompetitionFixture.Entered entered) {
        return api.get("/api/v1/competitions/" + entered.competitionId() + "/leaderboard", entered.token()).json();
    }

    private List<String> namesInOrder(JsonNode board) {
        List<String> names = new ArrayList<>();
        board.path("entries").forEach(entry -> names.add(entry.path("name").asText()));
        return names;
    }

    private JsonNode entryFor(JsonNode board, String name) {
        for (JsonNode entry : board.path("entries")) {
            if (entry.path("name").asText().equals(name)) {
                return entry;
            }
        }
        throw new AssertionError(name + " is not on the board");
    }

    @Test
    void aFiveTeamLeagueProducesACorrectPointsTable() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "league@example.com", 5);
        List<JsonNode> matches = drawMatches(entered);

        // A strict pecking order: the lower-numbered team always wins 2-0.
        for (int stronger = 1; stronger <= 5; stronger++) {
            for (int weaker = stronger + 1; weaker <= 5; weaker++) {
                ApiClient.Response response =
                    record(entered, matches, "Team " + stronger, 2, "Team " + weaker, 0);
                assertThat(response.status()).as("Team %d v Team %d", stronger, weaker).isEqualTo(200);
            }
        }

        JsonNode board = leaderboard(entered);

        assertThat(board.path("strategyKey").asText()).isEqualTo("POINTS_TABLE");
        assertThat(namesInOrder(board))
            .containsExactly("Team 1", "Team 2", "Team 3", "Team 4", "Team 5");

        JsonNode leader = entryFor(board, "Team 1");
        assertThat(leader.path("rank").asInt()).isEqualTo(1);
        assertThat(leader.path("metrics").path("played").asInt()).isEqualTo(4);
        assertThat(leader.path("metrics").path("won").asInt()).isEqualTo(4);
        assertThat(leader.path("metrics").path("lost").asInt()).isEqualTo(0);
        assertThat(leader.path("metrics").path("points").asInt()).isEqualTo(12);
        assertThat(leader.path("metrics").path("scoreFor").asInt()).isEqualTo(8);
        assertThat(leader.path("metrics").path("scoreAgainst").asInt()).isEqualTo(0);
        assertThat(leader.path("metrics").path("scoreDifference").asInt()).isEqualTo(8);

        JsonNode bottom = entryFor(board, "Team 5");
        assertThat(bottom.path("rank").asInt()).isEqualTo(5);
        assertThat(bottom.path("metrics").path("points").asInt()).isZero();
        assertThat(bottom.path("metrics").path("scoreDifference").asInt()).isEqualTo(-8);
    }

    @Test
    void teamsLevelOnPointsAreSeparatedByScoreDifference() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "tiebreak@example.com", 4);
        List<JsonNode> matches = drawMatches(entered);

        // Team 1 wins the league; the other three form a three-way cycle on three points each,
        // so nothing but the difference can order them.
        record(entered, matches, "Team 1", 1, "Team 2", 0);
        record(entered, matches, "Team 1", 1, "Team 3", 0);
        record(entered, matches, "Team 1", 1, "Team 4", 0);
        record(entered, matches, "Team 2", 5, "Team 3", 0);
        record(entered, matches, "Team 3", 2, "Team 4", 0);
        record(entered, matches, "Team 4", 1, "Team 2", 0);

        JsonNode board = leaderboard(entered);

        assertThat(entryFor(board, "Team 2").path("metrics").path("points").asInt()).isEqualTo(3);
        assertThat(entryFor(board, "Team 3").path("metrics").path("points").asInt()).isEqualTo(3);
        assertThat(entryFor(board, "Team 4").path("metrics").path("points").asInt()).isEqualTo(3);

        assertThat(entryFor(board, "Team 2").path("metrics").path("scoreDifference").asInt()).isEqualTo(3);
        assertThat(entryFor(board, "Team 4").path("metrics").path("scoreDifference").asInt()).isEqualTo(-2);
        assertThat(entryFor(board, "Team 3").path("metrics").path("scoreDifference").asInt()).isEqualTo(-4);

        assertThat(namesInOrder(board)).containsExactly("Team 1", "Team 2", "Team 4", "Team 3");
        assertThat(board.path("entries")).hasSize(4);
    }

    @Test
    void aWalkoverCountsTowardsTheTable() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "walkover@example.com", 4);
        List<JsonNode> matches = drawMatches(entered);

        JsonNode match = matches.get(0);
        String winnerId = match.path("participants").get(0).path("participantId").asText();
        String winnerName = match.path("participants").get(0).path("name").asText();

        ApiClient.Response response = api.post(
            "/api/v1/matches/" + match.path("id").asText() + "/result",
            Map.of("outcome", "WALKOVER", "winnerParticipantId", winnerId), entered.token());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().path("status").asText()).isEqualTo("WALKOVER");

        JsonNode entry = entryFor(leaderboard(entered), winnerName);
        assertThat(entry.path("metrics").path("played").asInt()).isEqualTo(1);
        assertThat(entry.path("metrics").path("won").asInt()).isEqualTo(1);
        assertThat(entry.path("metrics").path("points").asInt()).isEqualTo(3);
    }

    @Test
    void theBoardIsUnavailableUntilSomethingHasBeenPlayed() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "empty@example.com", 4);
        drawMatches(entered);

        ApiClient.Response response = api.get(
            "/api/v1/competitions/" + entered.competitionId() + "/leaderboard", entered.token());

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("LEADERBOARD_NOT_AVAILABLE");
    }

    @Test
    void theBoardIsRecomputedOnEveryResult() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "recompute@example.com", 4);
        List<JsonNode> matches = drawMatches(entered);

        record(entered, matches, "Team 3", 3, "Team 1", 0);
        assertThat(namesInOrder(leaderboard(entered))).first().isEqualTo("Team 3");

        record(entered, matches, "Team 2", 9, "Team 4", 0);
        assertThat(namesInOrder(leaderboard(entered))).first().isEqualTo("Team 2");
    }

    @Test
    void eightRunnersAreRankedByTheClock() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedIndividualCompetition(api, "sprint@example.com", 8);
        List<JsonNode> matches = drawMatches(entered);
        JsonNode race = matches.get(0);

        // Runner 1 is slowest through to Runner 8 fastest, so the board must invert the entry order.
        List<Map<String, Object>> scores = new ArrayList<>();
        race.path("participants").forEach(participant -> {
            int number = Integer.parseInt(participant.path("name").asText().replace("Runner ", ""));
            scores.add(Map.of(
                "participantId", participant.path("participantId").asText(),
                "value", 12.5 - (number * 0.1),
                "unit", "SECONDS"
            ));
        });

        ApiClient.Response response = api.post(
            "/api/v1/matches/" + race.path("id").asText() + "/result",
            Map.of("outcome", "COMPLETED", "scores", scores), entered.token());
        assertThat(response.status()).isEqualTo(200);

        JsonNode board = leaderboard(entered);

        assertThat(board.path("strategyKey").asText()).isEqualTo("LOWEST_TIME");
        assertThat(namesInOrder(board)).containsExactly(
            "Runner 8", "Runner 7", "Runner 6", "Runner 5",
            "Runner 4", "Runner 3", "Runner 2", "Runner 1");

        JsonNode winner = entryFor(board, "Runner 8");
        assertThat(winner.path("rank").asInt()).isEqualTo(1);
        assertThat(winner.path("metrics").path("bestValue").asDouble()).isEqualTo(11.700);
        assertThat(winner.path("metrics").path("unit").asText()).isEqualTo("SECONDS");
    }

    @Test
    void aRunnerWhoDidNotFinishIsRankedLastRatherThanDropped() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedIndividualCompetition(api, "dnf@example.com", 3);
        JsonNode race = drawMatches(entered).get(0);

        List<Map<String, Object>> scores = new ArrayList<>();
        race.path("participants").forEach(participant -> {
            String name = participant.path("name").asText();
            Map<String, Object> score = new LinkedHashMap<>();
            score.put("participantId", participant.path("participantId").asText());
            // Runner 1 pulled up; a null time is how that is recorded.
            score.put("value", name.equals("Runner 1") ? null : name.equals("Runner 2") ? 11.2 : 10.9);
            score.put("unit", "SECONDS");
            scores.add(score);
        });

        api.post("/api/v1/matches/" + race.path("id").asText() + "/result",
            Map.of("outcome", "COMPLETED", "scores", scores), entered.token());

        JsonNode board = leaderboard(entered);

        assertThat(namesInOrder(board)).containsExactly("Runner 3", "Runner 2", "Runner 1");
        assertThat(entryFor(board, "Runner 1").path("metrics").path("bestValue").isNull()).isTrue();
    }

    @Test
    void recordingTwiceOnOneMatchIsRefused() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "twice@example.com", 4);
        List<JsonNode> matches = drawMatches(entered);

        record(entered, matches, "Team 1", 2, "Team 2", 0);
        ApiClient.Response second = record(entered, matches, "Team 1", 3, "Team 2", 1);

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.errorCode()).isEqualTo("MATCH_ALREADY_COMPLETED");
    }

    @Test
    void aStaleVersionIsRejectedRatherThanOverwritingTheOtherOfficialsEntry() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "stale@example.com", 4);
        JsonNode match = drawMatches(entered).get(0);
        List<JsonNode> participants = new ArrayList<>();
        match.path("participants").forEach(participants::add);

        ApiClient.Response response = api.post(
            "/api/v1/matches/" + match.path("id").asText() + "/result", Map.of(
                "outcome", "COMPLETED",
                "version", 99,
                "scores", List.of(
                    Map.of("participantId", participants.get(0).path("participantId").asText(), "value", 1),
                    Map.of("participantId", participants.get(1).path("participantId").asText(), "value", 0)
                )
            ), entered.token());

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("STALE_VERSION");
    }

    @Test
    void aScoreForSomebodyOutsideTheMatchIsRejected() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "stray@example.com", 4);
        List<JsonNode> matches = drawMatches(entered);
        JsonNode match = matches.get(0);
        List<JsonNode> participants = new ArrayList<>();
        match.path("participants").forEach(participants::add);

        // Somebody who is in the competition, but not in this match.
        String outsiderId = entered.entrants().stream()
            .map(entrant -> entrant.participantId().toString())
            .filter(id -> participants.stream()
                .noneMatch(participant -> participant.path("participantId").asText().equals(id)))
            .findFirst()
            .orElseThrow();

        ApiClient.Response response = api.post(
            "/api/v1/matches/" + match.path("id").asText() + "/result", Map.of(
                "outcome", "COMPLETED",
                "scores", List.of(
                    Map.of("participantId", participants.get(0).path("participantId").asText(), "value", 1),
                    Map.of("participantId", outsiderId, "value", 0)
                )
            ), entered.token());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("INVALID_RESULT");
    }
}
