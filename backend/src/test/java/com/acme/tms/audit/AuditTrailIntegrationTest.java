package com.acme.tms.audit;

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

/** The trail as an auditor would actually read it, end to end. */
class AuditTrailIntegrationTest extends AbstractIntegrationTest {

    private List<JsonNode> auditLog(String token) {
        List<JsonNode> rows = new ArrayList<>();
        api.get("/api/v1/audit-logs?limit=200", token).json().forEach(rows::add);
        return rows;
    }

    private List<String> actionsOf(List<JsonNode> rows) {
        return rows.stream().map(row -> row.path("action").asText()).toList();
    }

    @Test
    void creatingATournamentIsRecordedWithItsActor() {
        ApiClient.Session session = api.registerTenant("audit@example.com", "Audit Federation");
        UUID organizationUnitId = UUID.fromString(
            api.get("/api/v1/organization-units", session.accessToken()).json().get(0).path("id").asText());

        UUID tournamentId = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId, "name", "Audited Games"), session.accessToken()).id();

        List<JsonNode> rows = auditLog(session.accessToken());
        JsonNode created = rows.stream()
            .filter(row -> row.path("action").asText().equals("tournament:create"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no tournament:create row in " + actionsOf(rows)));

        assertThat(created.path("entityType").asText()).isEqualTo("Tournament");
        assertThat(created.path("entityId").asText()).isEqualTo(tournamentId.toString());
        assertThat(created.path("actorId").asText()).isEqualTo(session.userId().toString());
        assertThat(created.path("actorName").asText()).isNotBlank();
        assertThat(created.path("organizationUnitId").asText()).isEqualTo(organizationUnitId.toString());
        // A create has nothing before it, and the state it produced afterwards.
        assertThat(created.path("beforeState").isNull()).isTrue();
        assertThat(created.path("afterState").path("name").asText()).isEqualTo("Audited Games");
    }

    @Test
    void anUpdateRecordsBothSidesOfTheChange() {
        ApiClient.Session session = api.registerTenant("beforeafter@example.com", "Diff Federation");
        UUID organizationUnitId = UUID.fromString(
            api.get("/api/v1/organization-units", session.accessToken()).json().get(0).path("id").asText());
        UUID tournamentId = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId, "name", "Original Name"), session.accessToken()).id();

        api.patch("/api/v1/tournaments/" + tournamentId,
            Map.of("name", "Corrected Name"), session.accessToken());

        JsonNode updated = auditLog(session.accessToken()).stream()
            .filter(row -> row.path("action").asText().equals("tournament:update"))
            .findFirst()
            .orElseThrow();

        assertThat(updated.path("beforeState").path("name").asText()).isEqualTo("Original Name");
        assertThat(updated.path("afterState").path("name").asText()).isEqualTo("Corrected Name");
    }

    @Test
    void aLifecycleTransitionIsRecorded() {
        ApiClient.Session session = api.registerTenant("transition@example.com", "Transition Federation");
        UUID organizationUnitId = UUID.fromString(
            api.get("/api/v1/organization-units", session.accessToken()).json().get(0).path("id").asText());
        UUID tournamentId = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId, "name", "Publishable"), session.accessToken()).id();

        api.post("/api/v1/tournaments/" + tournamentId + "/publish", Map.of(), session.accessToken());

        JsonNode row = auditLog(session.accessToken()).stream()
            .filter(entry -> entry.path("action").asText().equals("tournament:transition"))
            .findFirst()
            .orElseThrow();

        assertThat(row.path("beforeState").path("status").asText()).isEqualTo("DRAFT");
        assertThat(row.path("afterState").path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    void anInviteTokenNeverReachesTheTrail() {
        // InviteUserResponse carries the raw invite token. An audit row is append-only and
        // long-lived, so a credential that reaches one cannot be un-leaked.
        ApiClient.Session session = api.registerTenant("invite@example.com", "Invite Federation");
        UUID organizationUnitId = UUID.fromString(
            api.get("/api/v1/organization-units", session.accessToken()).json().get(0).path("id").asText());

        ApiClient.Response invited = api.post("/api/v1/users/invite", Map.of(
            "email", "newcomer@example.com",
            "displayName", "New Comer",
            "organizationUnitId", organizationUnitId
        ), session.accessToken());
        assertThat(invited.status()).isIn(200, 201);
        String realToken = invited.json().path("inviteToken").asText();
        assertThat(realToken).isNotBlank();

        JsonNode row = auditLog(session.accessToken()).stream()
            .filter(entry -> entry.path("action").asText().equals("user:invite"))
            .findFirst()
            .orElseThrow();

        // The property that matters is not that some field says "[redacted]" — it is that the raw
        // token appears nowhere in the trail at all. The snapshot is taken from the User read model
        // rather than the invite response, so it never carries the token in the first place; the
        // redactor is the second line of defence and is unit-tested directly.
        assertThat(row.toString()).doesNotContain(realToken);
        assertThat(auditLog(session.accessToken()).toString()).doesNotContain(realToken);
        assertThat(row.path("afterState").path("email").asText()).isEqualTo("newcomer@example.com");
        assertThat(row.path("afterState").has("roles")).isTrue();
    }

    @Test
    void grantingAuthorityIsRecorded() {
        // The single most important thing in the trail: who gave whom the ability to act.
        ApiClient.Session session = api.registerTenant("grant@example.com", "Grant Federation");
        UUID organizationUnitId = UUID.fromString(
            api.get("/api/v1/organization-units", session.accessToken()).json().get(0).path("id").asText());

        UUID invitedId = api.post("/api/v1/users/invite", Map.of(
            "email", "official@example.com",
            "displayName", "New Official",
            "organizationUnitId", organizationUnitId,
            "initialRole", Map.of("roleCode", "ORG_OFFICIAL",
                                  "scopeType", "ORGANIZATION",
                                  "scopeId", organizationUnitId)
        ), session.accessToken()).id();

        assertThat(actionsOf(auditLog(session.accessToken()))).contains("user:invite");
        assertThat(auditLog(session.accessToken()).stream()
            .anyMatch(row -> row.path("entityId").asText().equals(invitedId.toString()))).isTrue();
    }

    @Test
    void theTrailIsScopedToWhatTheCallerCanReach() {
        // An audit endpoint that ignored scope would be the most complete cross-tenant leak in the
        // product: it carries snapshots of every other module's data.
        ApiClient.Session mine = api.registerTenant("mine-audit@example.com", "Mine Federation");
        UUID myOrg = UUID.fromString(
            api.get("/api/v1/organization-units", mine.accessToken()).json().get(0).path("id").asText());
        api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", myOrg, "name", "My Secret Games"), mine.accessToken());

        ApiClient.Session theirs = api.registerTenant("theirs-audit@example.com", "Theirs Federation");

        List<JsonNode> theirRows = auditLog(theirs.accessToken());

        assertThat(theirRows)
            .as("no row from another tenant's subtree")
            .noneMatch(row -> row.path("organizationUnitId").asText().equals(myOrg.toString()));
        assertThat(theirRows.toString()).doesNotContain("My Secret Games");
    }

    @Test
    void theEntityHistoryEndpointReturnsOneEntitysChangesNewestFirst() {
        ApiClient.Session session = api.registerTenant("history@example.com", "History Federation");
        UUID organizationUnitId = UUID.fromString(
            api.get("/api/v1/organization-units", session.accessToken()).json().get(0).path("id").asText());
        UUID tournamentId = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", organizationUnitId, "name", "Busy Games"), session.accessToken()).id();

        api.patch("/api/v1/tournaments/" + tournamentId, Map.of("name", "Busier Games"), session.accessToken());
        api.post("/api/v1/tournaments/" + tournamentId + "/publish", Map.of(), session.accessToken());

        JsonNode history = api.get(
            "/api/v1/audit-logs/entity?entityType=Tournament&entityId=" + tournamentId,
            session.accessToken()).json();

        List<String> actions = new ArrayList<>();
        history.forEach(row -> actions.add(row.path("action").asText()));

        assertThat(actions).containsExactly(
            "tournament:transition", "tournament:update", "tournament:create");
        history.forEach(row ->
            assertThat(row.path("entityId").asText()).isEqualTo(tournamentId.toString()));
    }

    @Test
    void recordingAResultIsAuditedAgainstItsMatch() {
        CompetitionFixture.Entered entered =
            CompetitionFixture.closedTeamCompetition(api, "resultaudit@example.com", 4);

        JsonNode body = api.post("/api/v1/competitions/" + entered.competitionId() + "/fixtures/generate",
            Map.of(), entered.token()).json();
        JsonNode match = body.path("fixtures").get(0).path("matches").get(0);
        List<JsonNode> participants = new ArrayList<>();
        match.path("participants").forEach(participants::add);

        api.post("/api/v1/matches/" + match.path("id").asText() + "/result", Map.of(
            "outcome", "COMPLETED",
            "scores", List.of(
                Map.of("participantId", participants.get(0).path("participantId").asText(), "value", 2),
                Map.of("participantId", participants.get(1).path("participantId").asText(), "value", 0))
        ), entered.token());

        JsonNode history = api.get(
            "/api/v1/audit-logs/entity?entityType=Match&entityId=" + match.path("id").asText(),
            entered.token()).json();

        JsonNode recorded = history.get(0);
        assertThat(recorded.path("action").asText()).isEqualTo("result:record");
        // The correction flow depends on both sides being recoverable (BR-RES-3).
        assertThat(recorded.path("beforeState").path("status").asText()).isEqualTo("SCHEDULED");
        assertThat(recorded.path("afterState").path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void aRefusedCallLeavesNoTrace() {
        // The audit aspect runs after the permission aspect on purpose: a call that was refused
        // has not happened, and recording it as though it had would make the trail misleading.
        ApiClient.Session mine = api.registerTenant("refused@example.com", "Refused Federation");
        UUID myOrg = UUID.fromString(
            api.get("/api/v1/organization-units", mine.accessToken()).json().get(0).path("id").asText());

        ApiClient.Session intruder = api.registerTenant("intruder@example.com", "Intruder Federation");
        ApiClient.Response refused = api.post("/api/v1/tournaments", Map.of(
            "organizationUnitId", myOrg, "name", "Not Yours"), intruder.accessToken());

        assertThat(refused.status()).isEqualTo(403);
        assertThat(auditLog(mine.accessToken()).toString()).doesNotContain("Not Yours");
    }
}
