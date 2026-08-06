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

/**
 * Whether an entry needs a human decision is configured per tournament, falling back to the owning
 * organization. 07_APPROVAL_WORKFLOW_ENGINE section 7.2 defines the two policies; this covers
 * resolution, the effect on submission, and the rule that changing the setting is not retroactive.
 */
class ApprovalPolicyIntegrationTest extends AbstractIntegrationTest {

    private static final Map<String, Object> SCHEMA = Map.of(
        "type", "object",
        "required", List.of("jerseyName"),
        "properties", Map.of("jerseyName", Map.of("type", "string")),
        "additionalProperties", false
    );

    private CompetitionFixture openCompetition(String email) {
        CompetitionFixture fixture = CompetitionFixture.openIndividualCompetition(api, email);
        api.post("/api/v1/competitions/" + fixture.competitionId() + "/form-definitions",
            Map.of("schema", SCHEMA), fixture.organizerToken());
        return fixture;
    }

    private ApiClient.Response submit(CompetitionFixture fixture, String name) {
        return api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participant", Map.of("participantType", "INDIVIDUAL", "displayName", name),
            "answers", Map.of("jerseyName", name)
        ), fixture.organizerToken());
    }

    private ApiClient.Response setPolicy(CompetitionFixture fixture, String policy) {
        return api.patch("/api/v1/tournaments/" + fixture.tournamentId(),
            Map.of("approvalPolicy", policy), fixture.organizerToken());
    }

    @Test
    void reviewIsTheDefaultSoEntriesArrivePending() {
        CompetitionFixture fixture = openCompetition("default@example.com");

        JsonNode tournament = api.get("/api/v1/tournaments/" + fixture.tournamentId(), fixture.organizerToken()).json();
        assertThat(tournament.path("approvalPolicy").isNull())
            .as("no override until the organizer sets one")
            .isTrue();
        assertThat(tournament.path("effectiveApprovalPolicy").asText()).isEqualTo("DIRECT_SINGLE_APPROVAL");

        assertThat(submit(fixture, "Ravi Kumar").json().path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void autoApproveLandsEntriesApprovedWithoutAnyoneActing() {
        CompetitionFixture fixture = openCompetition("auto@example.com");
        assertThat(setPolicy(fixture, "AUTO_APPROVE").status()).isEqualTo(200);

        JsonNode registration = submit(fixture, "Ravi Kumar").json();

        assertThat(registration.path("status").asText()).isEqualTo("APPROVED");
        assertThat(registration.path("decidedAt").isNull())
            .as("an automatic decision is still a decision, and is timestamped")
            .isFalse();
    }

    @Test
    void theTournamentSettingIsReportedBackAsBothOverrideAndEffective() {
        CompetitionFixture fixture = openCompetition("report@example.com");
        setPolicy(fixture, "AUTO_APPROVE");

        JsonNode tournament = api.get("/api/v1/tournaments/" + fixture.tournamentId(), fixture.organizerToken()).json();

        assertThat(tournament.path("approvalPolicy").asText()).isEqualTo("AUTO_APPROVE");
        assertThat(tournament.path("effectiveApprovalPolicy").asText()).isEqualTo("AUTO_APPROVE");
    }

    @Test
    void inheritClearsTheOverrideAndFallsBackToTheOrganization() {
        CompetitionFixture fixture = openCompetition("inherit@example.com");
        setPolicy(fixture, "AUTO_APPROVE");

        assertThat(setPolicy(fixture, "INHERIT").status()).isEqualTo(200);

        JsonNode tournament = api.get("/api/v1/tournaments/" + fixture.tournamentId(), fixture.organizerToken()).json();
        assertThat(tournament.path("approvalPolicy").isNull()).isTrue();
        assertThat(tournament.path("effectiveApprovalPolicy").asText()).isEqualTo("DIRECT_SINGLE_APPROVAL");

        assertThat(submit(fixture, "Back To Review").json().path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void changingThePolicyLeavesExistingEntriesAlone() {
        // The rule that makes this safe to toggle: a decision already taken, or still owed, is
        // never rewritten by flipping the switch.
        CompetitionFixture fixture = openCompetition("retro@example.com");

        UUID pendingId = submit(fixture, "Submitted Under Review").id();
        setPolicy(fixture, "AUTO_APPROVE");

        assertThat(api.get("/api/v1/registrations/" + pendingId, fixture.organizerToken())
            .json().path("status").asText())
            .as("the earlier entry still awaits a human")
            .isEqualTo("PENDING");

        UUID autoId = submit(fixture, "Submitted Under Auto").id();
        setPolicy(fixture, "DIRECT_SINGLE_APPROVAL");

        assertThat(api.get("/api/v1/registrations/" + autoId, fixture.organizerToken())
            .json().path("status").asText())
            .as("and the auto-approved one stays approved")
            .isEqualTo("APPROVED");
    }

    @Test
    void anAutoApprovedEntryStillCountsTowardsTheCap() {
        CompetitionFixture fixture = CompetitionFixture.openTeamCompetition(api, "autocap@example.com", 1);
        api.post("/api/v1/competitions/" + fixture.competitionId() + "/form-definitions",
            Map.of("schema", SCHEMA), fixture.organizerToken());
        setPolicy(fixture, "AUTO_APPROVE");

        Map<String, Object> team = Map.of(
            "participantType", "TEAM",
            "displayName", "First FC",
            "members", List.of(
                Map.of("fullName", "A Captain", "memberRole", "CAPTAIN"),
                Map.of("fullName", "A Player", "memberRole", "PLAYER"))
        );
        assertThat(api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participant", team,
            "answers", Map.of("jerseyName", "First FC")
        ), fixture.organizerToken()).status()).isEqualTo(201);

        ApiClient.Response overflow = api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participant", Map.of(
                "participantType", "TEAM",
                "displayName", "Second FC",
                "members", List.of(
                    Map.of("fullName", "B Captain", "memberRole", "CAPTAIN"),
                    Map.of("fullName", "B Player", "memberRole", "PLAYER"))),
            "answers", Map.of("jerseyName", "Second FC")
        ), fixture.organizerToken());

        assertThat(overflow.status()).isEqualTo(409);
        assertThat(overflow.errorCode()).isEqualTo("COMPETITION_FULL");
    }

    @Test
    void anUnknownPolicyValueIsRejected() {
        CompetitionFixture fixture = openCompetition("bogus@example.com");

        ApiClient.Response response = setPolicy(fixture, "MAYBE_LATER");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("INVALID_APPROVAL_POLICY");
    }
}
