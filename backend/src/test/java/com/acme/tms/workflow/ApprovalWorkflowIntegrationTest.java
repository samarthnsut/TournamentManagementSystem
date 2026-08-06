package com.acme.tms.workflow;

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
 * The frozen brief promises a one-level chain and a three-level chain differ by configuration
 * alone. These tests run both through the same endpoints and assert that nothing but the number of
 * approvals changes.
 */
class ApprovalWorkflowIntegrationTest extends AbstractIntegrationTest {

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

    private UUID submit(CompetitionFixture fixture, String name) {
        return api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participant", Map.of("participantType", "INDIVIDUAL", "displayName", name),
            "answers", Map.of("jerseyName", name)
        ), fixture.organizerToken()).id();
    }

    private ApiClient.Response createWorkflow(CompetitionFixture fixture, String name, List<Map<String, Object>> steps) {
        return api.post("/api/v1/approval-workflows", Map.of(
            "organizationUnitId", fixture.organizationUnitId(),
            "workflowName", name,
            "steps", steps
        ), fixture.organizerToken());
    }

    private String statusOf(CompetitionFixture fixture, UUID registrationId) {
        return api.get("/api/v1/registrations/" + registrationId, fixture.organizerToken())
            .json().path("status").asText();
    }

    @Test
    void withNoWorkflowAnEntryStillWaitsForSomeoneToApprove() {
        // Doc 07 section 7.2: the default policy synthesises an implicit single step rather than
        // letting an entry through unreviewed.
        CompetitionFixture fixture = openCompetition("implicit@example.com");
        UUID registrationId = submit(fixture, "Ravi Kumar");

        JsonNode approval = api.get("/api/v1/registrations/" + registrationId + "/approval",
            fixture.organizerToken()).json();

        assertThat(approval.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(approval.path("totalLevels").asInt()).isEqualTo(1);
        assertThat(statusOf(fixture, registrationId)).isEqualTo("PENDING");
    }

    @Test
    void aSingleStepChainNeedsOneApproval() {
        CompetitionFixture fixture = openCompetition("single@example.com");
        assertThat(createWorkflow(fixture, "Club Quick Approve", List.of(
            Map.of("level", 1, "roleCode", "TENANT_ADMIN", "stepName", "Organizer Review")
        )).status()).isEqualTo(201);

        UUID registrationId = submit(fixture, "Ravi Kumar");

        ApiClient.Response approved = api.post("/api/v1/registrations/" + registrationId + "/approve",
            Map.of(), fixture.organizerToken());

        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.json().path("status").asText()).isEqualTo("APPROVED");
        assertThat(statusOf(fixture, registrationId)).isEqualTo("APPROVED");
    }

    @Test
    void aThreeStepChainNeedsThreeApprovalsAndTheSameCode() {
        CompetitionFixture fixture = openCompetition("three@example.com");
        createWorkflow(fixture, "SAI Registration Chain", List.of(
            Map.of("level", 1, "roleCode", "TENANT_ADMIN", "stepName", "District Verification"),
            Map.of("level", 2, "roleCode", "TENANT_ADMIN", "stepName", "State Approval"),
            Map.of("level", 3, "roleCode", "TENANT_ADMIN", "stepName", "National Ratification")
        ));

        UUID registrationId = submit(fixture, "Ravi Kumar");

        JsonNode first = api.post("/api/v1/registrations/" + registrationId + "/approve",
            Map.of(), fixture.organizerToken()).json();
        assertThat(first.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(first.path("currentLevel").asInt()).isEqualTo(2);
        assertThat(first.path("progressLabel").asText()).isEqualTo("Awaiting State Approval (2 of 3)");
        assertThat(statusOf(fixture, registrationId))
            .as("the registration stays PENDING while the chain is mid-flight")
            .isEqualTo("PENDING");

        api.post("/api/v1/registrations/" + registrationId + "/approve", Map.of(), fixture.organizerToken());

        JsonNode third = api.post("/api/v1/registrations/" + registrationId + "/approve",
            Map.of(), fixture.organizerToken()).json();
        assertThat(third.path("status").asText()).isEqualTo("APPROVED");
        assertThat(statusOf(fixture, registrationId)).isEqualTo("APPROVED");
    }

    @Test
    void rejectingAtAnyLevelEndsTheChainAndTheEntry() {
        CompetitionFixture fixture = openCompetition("reject@example.com");
        createWorkflow(fixture, "Three Level", List.of(
            Map.of("level", 1, "roleCode", "TENANT_ADMIN"),
            Map.of("level", 2, "roleCode", "TENANT_ADMIN"),
            Map.of("level", 3, "roleCode", "TENANT_ADMIN")
        ));
        UUID registrationId = submit(fixture, "Ravi Kumar");
        api.post("/api/v1/registrations/" + registrationId + "/approve", Map.of(), fixture.organizerToken());

        JsonNode rejected = api.post("/api/v1/registrations/" + registrationId + "/reject",
            Map.of("comment", "Age documents missing"), fixture.organizerToken()).json();

        assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        assertThat(statusOf(fixture, registrationId)).isEqualTo("REJECTED");
        assertThat(rejected.path("actions")).hasSize(2);
    }

    @Test
    void rejectingWithoutAReasonIsRefused() {
        CompetitionFixture fixture = openCompetition("noreason@example.com");
        UUID registrationId = submit(fixture, "Ravi Kumar");

        ApiClient.Response response = api.post("/api/v1/registrations/" + registrationId + "/reject",
            Map.of("comment", "  "), fixture.organizerToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("COMMENT_REQUIRED");
    }

    @Test
    void notifyOnlyLevelsAreSkippedRatherThanWaitedOn() {
        CompetitionFixture fixture = openCompetition("notify@example.com");
        createWorkflow(fixture, "With Notify Step", List.of(
            Map.of("level", 1, "roleCode", "TENANT_ADMIN", "stepName", "Verification"),
            Map.of("level", 2, "roleCode", "TENANT_ADMIN", "stepName", "FYI", "approvalRequired", false),
            Map.of("level", 3, "roleCode", "TENANT_ADMIN", "stepName", "Ratification")
        ));
        UUID registrationId = submit(fixture, "Ravi Kumar");

        JsonNode first = api.post("/api/v1/registrations/" + registrationId + "/approve",
            Map.of(), fixture.organizerToken()).json();

        assertThat(first.path("currentLevel").asInt()).as("level 2 is notify-only").isEqualTo(3);
        assertThat(first.path("totalLevels").asInt()).as("and is not counted").isEqualTo(2);
    }

    @Test
    void withdrawingAbandonsTheOpenChain() {
        CompetitionFixture fixture = openCompetition("cancel@example.com");
        UUID registrationId = submit(fixture, "Ravi Kumar");

        api.post("/api/v1/registrations/" + registrationId + "/withdraw", Map.of(), fixture.organizerToken());

        assertThat(api.get("/api/v1/registrations/" + registrationId + "/approval", fixture.organizerToken())
            .json().path("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void aDecidedEntryCannotBeDecidedAgain() {
        CompetitionFixture fixture = openCompetition("twice@example.com");
        UUID registrationId = submit(fixture, "Ravi Kumar");
        api.post("/api/v1/registrations/" + registrationId + "/approve", Map.of(), fixture.organizerToken());

        ApiClient.Response second = api.post("/api/v1/registrations/" + registrationId + "/approve",
            Map.of(), fixture.organizerToken());

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.errorCode()).isEqualTo("NO_OPEN_APPROVAL");
    }

    @Test
    void anAutoApprovingTournamentOpensNoChainAtAll() {
        CompetitionFixture fixture = openCompetition("auto@example.com");
        api.patch("/api/v1/tournaments/" + fixture.tournamentId(),
            Map.of("approvalPolicy", "AUTO_APPROVE"), fixture.organizerToken());

        UUID registrationId = submit(fixture, "Ravi Kumar");

        assertThat(statusOf(fixture, registrationId)).isEqualTo("APPROVED");
        assertThat(api.get("/api/v1/registrations/" + registrationId + "/approval", fixture.organizerToken())
            .status())
            .as("nothing was ever queued for review")
            .isEqualTo(404);
    }

    @Test
    void theInboxShowsWhatIsWaitingOnTheCaller() {
        CompetitionFixture fixture = openCompetition("inbox@example.com");
        submit(fixture, "Ravi Kumar");
        submit(fixture, "Priya Sharma");

        JsonNode inbox = api.get("/api/v1/approvals/inbox", fixture.organizerToken()).json();

        assertThat(inbox).hasSize(2);
        assertThat(inbox.findValuesAsText("participantName"))
            .containsExactlyInAnyOrder("Ravi Kumar", "Priya Sharma");
        assertThat(inbox.get(0).path("progressLabel").asText()).isNotBlank();
    }

    @Test
    void anotherTenantsWorkIsNotInMyInbox() {
        CompetitionFixture mine = openCompetition("mine@example.com");
        CompetitionFixture theirs = openCompetition("theirs@example.com");
        submit(theirs, "Their Entrant");

        JsonNode inbox = api.get("/api/v1/approvals/inbox", mine.organizerToken()).json();

        assertThat(inbox).isEmpty();
    }

    @Test
    void decidingAnotherTenantsEntryIsRefused() {
        CompetitionFixture owner = openCompetition("owner2@example.com");
        CompetitionFixture intruder = openCompetition("intruder2@example.com");
        UUID registrationId = submit(owner, "Ravi Kumar");

        ApiClient.Response response = api.post("/api/v1/registrations/" + registrationId + "/approve",
            Map.of(), intruder.organizerToken());

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void aChainThatReviewsNothingIsRejectedAtConfigurationTime() {
        CompetitionFixture fixture = openCompetition("nosteps@example.com");

        ApiClient.Response response = createWorkflow(fixture, "Pointless", List.of(
            Map.of("level", 1, "roleCode", "TENANT_ADMIN", "approvalRequired", false)
        ));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("NO_ACTIONABLE_STEP");
    }

    @Test
    void publishingANewChainStandsTheOldOneDown() {
        CompetitionFixture fixture = openCompetition("replace@example.com");
        createWorkflow(fixture, "First", List.of(Map.of("level", 1, "roleCode", "TENANT_ADMIN")));
        createWorkflow(fixture, "Second", List.of(
            Map.of("level", 1, "roleCode", "TENANT_ADMIN"),
            Map.of("level", 2, "roleCode", "TENANT_ADMIN")
        ));

        JsonNode workflows = api.get("/api/v1/approval-workflows", fixture.organizerToken()).json();
        assertThat(workflows).hasSize(2);

        // A new entry follows the chain that is active now.
        UUID registrationId = submit(fixture, "Ravi Kumar");
        assertThat(api.get("/api/v1/registrations/" + registrationId + "/approval", fixture.organizerToken())
            .json().path("totalLevels").asInt()).isEqualTo(2);
    }
}
