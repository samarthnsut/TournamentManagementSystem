package com.acme.tms.registration;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;
import com.acme.tms.support.CompetitionFixture;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationIntegrationTest extends AbstractIntegrationTest {

    private static final Map<String, Object> SIMPLE_SCHEMA = Map.of(
        "type", "object",
        "required", List.of("jerseyName"),
        "properties", Map.of("jerseyName", Map.of("type", "string")),
        "additionalProperties", false
    );

    private void publishForm(CompetitionFixture fixture) {
        api.post("/api/v1/competitions/" + fixture.competitionId() + "/form-definitions",
            Map.of("schema", SIMPLE_SCHEMA), fixture.organizerToken());
    }

    private ApiClient.Response submit(CompetitionFixture fixture, String name, String participantType) {
        Map<String, Object> participant = participantType.equals("TEAM")
            // The team configuration sets teamSize.min = 2, so a bare team is not a valid entry.
            ? Map.of(
                "participantType", participantType,
                "displayName", name,
                "members", List.of(
                    Map.of("fullName", name + " Captain", "memberRole", "CAPTAIN"),
                    Map.of("fullName", name + " Player", "memberRole", "PLAYER")
                ))
            : Map.of("participantType", participantType, "displayName", name);

        return api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participant", participant,
            "answers", Map.of("jerseyName", name)
        ), fixture.organizerToken());
    }

    @Test
    void submittingCreatesAPendingRegistrationAndItsParticipant() {
        CompetitionFixture fixture = CompetitionFixture.openIndividualCompetition(api, "submit@example.com");
        publishForm(fixture);

        ApiClient.Response response = submit(fixture, "Ravi Kumar", "INDIVIDUAL");

        assertThat(response.status()).isEqualTo(201);
        JsonNode body = response.json();
        assertThat(body.path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("competitionId").asText()).isEqualTo(fixture.competitionId().toString());
        assertThat(body.path("participant").path("displayName").asText()).isEqualTo("Ravi Kumar");
        assertThat(body.path("participant").path("participantType").asText()).isEqualTo("INDIVIDUAL");
        assertThat(body.path("submittedAt").isMissingNode()).isFalse();
    }

    @Test
    void theSameParticipantCannotHoldTwoLiveRegistrations() {
        CompetitionFixture fixture = CompetitionFixture.openIndividualCompetition(api, "dupe@example.com");
        publishForm(fixture);

        UUID participantId = UUID.fromString(
            submit(fixture, "Ravi Kumar", "INDIVIDUAL").json().path("participant").path("id").asText());

        ApiClient.Response duplicate = api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participantId", participantId,
            "answers", Map.of("jerseyName", "Ravi Kumar")
        ), fixture.organizerToken());

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.errorCode()).isEqualTo("ALREADY_REGISTERED");
    }

    @Test
    void withdrawingFreesTheSlotForAFreshRegistration() {
        CompetitionFixture fixture = CompetitionFixture.openIndividualCompetition(api, "withdraw@example.com");
        publishForm(fixture);

        ApiClient.Response first = submit(fixture, "Ravi Kumar", "INDIVIDUAL");
        UUID registrationId = first.id();
        UUID participantId = UUID.fromString(first.json().path("participant").path("id").asText());

        ApiClient.Response withdrawn = api.post(
            "/api/v1/registrations/" + registrationId + "/withdraw", Map.of(), fixture.organizerToken());
        assertThat(withdrawn.status()).isEqualTo(200);
        assertThat(withdrawn.json().path("status").asText()).isEqualTo("WITHDRAWN");

        // The partial unique index excludes WITHDRAWN rows, so re-entry is allowed.
        ApiClient.Response again = api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participantId", participantId,
            "answers", Map.of("jerseyName", "Ravi Kumar")
        ), fixture.organizerToken());
        assertThat(again.status()).isEqualTo(201);
    }

    @Test
    void withdrawingTwiceIsRejected() {
        CompetitionFixture fixture = CompetitionFixture.openIndividualCompetition(api, "withdraw2@example.com");
        publishForm(fixture);
        UUID registrationId = submit(fixture, "Ravi Kumar", "INDIVIDUAL").id();

        api.post("/api/v1/registrations/" + registrationId + "/withdraw", Map.of(), fixture.organizerToken());
        ApiClient.Response second = api.post(
            "/api/v1/registrations/" + registrationId + "/withdraw", Map.of(), fixture.organizerToken());

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.errorCode()).isEqualTo("ALREADY_FINALIZED");
    }

    @Test
    void participantTypeMustMatchTheCompetitionConfiguration() {
        // Athletics is configured for INDIVIDUAL entrants; a team cannot enter it (BR-REG-3).
        CompetitionFixture fixture = CompetitionFixture.openIndividualCompetition(api, "wrongtype@example.com");
        publishForm(fixture);

        ApiClient.Response response = submit(fixture, "Sonipat FC", "TEAM");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("PARTICIPANT_TYPE_MISMATCH");
    }

    @Test
    void registrationIsRefusedWhileTheCompetitionIsNotOpen() {
        CompetitionFixture fixture = CompetitionFixture.openIndividualCompetition(api, "closed@example.com");
        publishForm(fixture);
        api.post("/api/v1/competitions/" + fixture.competitionId() + "/close", Map.of(), fixture.organizerToken());

        ApiClient.Response response = submit(fixture, "Latecomer", "INDIVIDUAL");

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("COMPETITION_NOT_OPEN");
    }

    @Test
    void submissionsBeyondTheCapAreRejected() {
        CompetitionFixture fixture = CompetitionFixture.openTeamCompetition(api, "capped@example.com", 1);
        publishForm(fixture);

        assertThat(submit(fixture, "First FC", "TEAM").status()).isEqualTo(201);

        ApiClient.Response overflow = submit(fixture, "Second FC", "TEAM");
        assertThat(overflow.status()).isEqualTo(409);
        assertThat(overflow.errorCode()).isEqualTo("COMPETITION_FULL");
    }

    @Test
    void aWithdrawnEntryDoesNotCountTowardsTheCap() {
        CompetitionFixture fixture = CompetitionFixture.openTeamCompetition(api, "capfree@example.com", 1);
        publishForm(fixture);

        UUID registrationId = submit(fixture, "First FC", "TEAM").id();
        api.post("/api/v1/registrations/" + registrationId + "/withdraw", Map.of(), fixture.organizerToken());

        assertThat(submit(fixture, "Second FC", "TEAM").status()).isEqualTo(201);
    }

    @Test
    void aTeamRegistrationCarriesItsRoster() {
        CompetitionFixture fixture = CompetitionFixture.openTeamCompetition(api, "roster@example.com");
        publishForm(fixture);

        ApiClient.Response response = api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participant", Map.of(
                "participantType", "TEAM",
                "displayName", "Sonipat FC",
                "members", List.of(
                    Map.of("fullName", "Aman Malik", "memberRole", "CAPTAIN", "jerseyNumber", 10),
                    Map.of("fullName", "Vikram Singh", "memberRole", "PLAYER", "jerseyNumber", 7)
                )
            ),
            "answers", Map.of("jerseyName", "Sonipat FC")
        ), fixture.organizerToken());

        assertThat(response.status()).isEqualTo(201);
        JsonNode members = response.json().path("participant").path("members");
        assertThat(members).hasSize(2);
        assertThat(members.get(0).path("memberRole").asText()).isEqualTo("CAPTAIN");
    }

    @Test
    void aTeamCannotHaveTwoCaptains() {
        CompetitionFixture fixture = CompetitionFixture.openTeamCompetition(api, "captains@example.com");
        publishForm(fixture);

        ApiClient.Response response = api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participant", Map.of(
                "participantType", "TEAM",
                "displayName", "Two Captains FC",
                "members", List.of(
                    Map.of("fullName", "Aman Malik", "memberRole", "CAPTAIN"),
                    Map.of("fullName", "Vikram Singh", "memberRole", "CAPTAIN")
                )
            ),
            "answers", Map.of("jerseyName", "Two Captains FC")
        ), fixture.organizerToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("MULTIPLE_CAPTAINS");
    }

    @Test
    void rosterSizeIsCheckedAgainstTheSportRules() {
        // teamConfig sets teamSize.min = 2, so a single-player squad is not a valid entry.
        CompetitionFixture fixture = CompetitionFixture.openTeamCompetition(api, "squad@example.com");
        publishForm(fixture);

        ApiClient.Response response = api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participant", Map.of(
                "participantType", "TEAM",
                "displayName", "Lonely FC",
                "members", List.of(Map.of("fullName", "Solo Player", "memberRole", "CAPTAIN"))
            ),
            "answers", Map.of("jerseyName", "Lonely FC")
        ), fixture.organizerToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("INVALID_ROSTER_SIZE");
    }

    @Test
    void registrationsAreListableForACompetition() {
        CompetitionFixture fixture = CompetitionFixture.openIndividualCompetition(api, "list@example.com");
        publishForm(fixture);
        submit(fixture, "Runner One", "INDIVIDUAL");
        submit(fixture, "Runner Two", "INDIVIDUAL");

        JsonNode registrations = api.get(
            "/api/v1/competitions/" + fixture.competitionId() + "/registrations", fixture.organizerToken()).json();

        assertThat(registrations).hasSize(2);
        assertThat(registrations.findValuesAsText("status")).containsOnly("PENDING");
    }

    @Test
    void anotherTenantCannotReadTheseRegistrations() {
        CompetitionFixture fixture = CompetitionFixture.openIndividualCompetition(api, "owner@example.com");
        publishForm(fixture);
        UUID registrationId = submit(fixture, "Ravi Kumar", "INDIVIDUAL").id();

        ApiClient.Session intruder = api.registerTenant("intruder@example.com", "Other Federation");

        assertThat(api.get("/api/v1/registrations/" + registrationId, intruder.accessToken()).status())
            .isEqualTo(403);
        assertThat(api.get("/api/v1/competitions/" + fixture.competitionId() + "/registrations",
            intruder.accessToken()).status()).isEqualTo(403);
    }

    @Test
    void anonymousCallersCannotRegister() {
        CompetitionFixture fixture = CompetitionFixture.openIndividualCompetition(api, "anon@example.com");
        publishForm(fixture);

        ApiClient.Response response = api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participant", Map.of("participantType", "INDIVIDUAL", "displayName", "Anon"),
            "answers", Map.of("jerseyName", "Anon")
        ), null);

        assertThat(response.status()).isEqualTo(401);
    }
}
