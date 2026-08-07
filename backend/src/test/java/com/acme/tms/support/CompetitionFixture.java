package com.acme.tms.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final Map<String, Object> ENTRY_SCHEMA = Map.of(
        "type", "object",
        "required", List.of("jerseyName"),
        "properties", Map.of("jerseyName", Map.of("type", "string")),
        "additionalProperties", false
    );

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

    /**
     * A Football competition CLOSED with {@code teams} approved entrants — the state fixture
     * generation demands (BR-F-2), which is where every Sprint 6 test starts.
     *
     * @return the entrants in submission order, so a test can name the team it expects to win
     */
    public static Entered closedTeamCompetition(ApiClient api, String email, int teams) {
        CompetitionFixture fixture = open(api, email, "FOOTBALL", teamConfig(), null);
        return fixture.enterAndClose(api, teams, "TEAM", "Team ");
    }

    /** An Athletics competition CLOSED with {@code runners} approved entrants. */
    public static Entered closedIndividualCompetition(ApiClient api, String email, int runners) {
        CompetitionFixture fixture = open(api, email, "ATHLETICS_100M", individualConfig(), null);
        return fixture.enterAndClose(api, runners, "INDIVIDUAL", "Runner ");
    }

    /**
     * Auto-approve rather than approving each entry by hand: these tests are about what happens
     * after entries are settled, and driving the approval engine again here would only couple them
     * to Sprint 5's behaviour.
     */
    private Entered enterAndClose(ApiClient api, int count, String participantType, String namePrefix) {
        api.patch("/api/v1/tournaments/" + tournamentId,
            Map.of("approvalPolicy", "AUTO_APPROVE"), organizerToken);
        api.post("/api/v1/competitions/" + competitionId + "/form-definitions",
            Map.of("schema", ENTRY_SCHEMA), organizerToken);

        List<Entrant> entrants = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            String name = namePrefix + index;
            JsonNode body = api.post("/api/v1/registrations", Map.of(
                "competitionId", competitionId,
                "participant", participantOf(participantType, name),
                "answers", Map.of("jerseyName", name)
            ), organizerToken).json();

            entrants.add(new Entrant(
                UUID.fromString(body.path("participant").path("id").asText()),
                name
            ));
        }

        api.post("/api/v1/competitions/" + competitionId + "/close", Map.of(), organizerToken);
        return new Entered(this, List.copyOf(entrants));
    }

    private static Map<String, Object> participantOf(String participantType, String name) {
        if (!participantType.equals("TEAM")) {
            return Map.of("participantType", participantType, "displayName", name);
        }
        // teamSize.min is 2, so a bare team is not a valid entry.
        return Map.of(
            "participantType", participantType,
            "displayName", name,
            "members", List.of(
                Map.of("fullName", name + " Captain", "memberRole", "CAPTAIN"),
                Map.of("fullName", name + " Player", "memberRole", "PLAYER")
            )
        );
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
            "tiebreakers", List.of("SCORE_DIFFERENCE", "SCORE_FOR", "HEAD_TO_HEAD"),
            "teamSize", Map.of("min", 2, "max", 18)
        ));
        return config;
    }

    /** A closed competition plus the entrants that were approved into it. */
    public record Entered(CompetitionFixture fixture, List<Entrant> entrants) {

        public String token() {
            return fixture.organizerToken();
        }

        public UUID competitionId() {
            return fixture.competitionId();
        }

        public Entrant entrant(int index) {
            return entrants.get(index);
        }
    }

    public record Entrant(UUID participantId, String displayName) {
    }
}
