package com.acme.tms.access;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScopedAccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    void haryanaAdminCannotReachPunjab() {
        ApiClient.Session haryana = api.registerTenant("haryana@example.com", "Haryana State Association");
        ApiClient.Session punjab = api.registerTenant("punjab@example.com", "Punjab State Association");

        UUID punjabRootId = rootUnitId(punjab);

        assertThat(api.get("/api/v1/organization-units/" + punjabRootId, haryana.accessToken()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/organization-units/" + punjabRootId + "/tree", haryana.accessToken()).status()).isEqualTo(403);
        assertThat(api.patch("/api/v1/organization-units/" + punjabRootId, Map.of("name", "Hijacked"), haryana.accessToken()).status())
            .isEqualTo(403);
        assertThat(api.delete("/api/v1/organization-units/" + punjabRootId, haryana.accessToken()).status()).isEqualTo(403);
    }

    @Test
    void listingOnlyReturnsUnitsInsideTheCallersSubtree() {
        ApiClient.Session haryana = api.registerTenant("list-haryana@example.com", "Haryana Listing Association");
        api.registerTenant("list-punjab@example.com", "Punjab Listing Association");

        UUID haryanaRootId = rootUnitId(haryana);
        api.post("/api/v1/organization-units", Map.of(
            "parentOrganizationUnitId", haryanaRootId,
            "name", "Sonipat District Association",
            "type", "DISTRICT_ASSOCIATION"
        ), haryana.accessToken());

        JsonNode visible = api.get("/api/v1/organization-units", haryana.accessToken()).json();

        assertThat(visible).hasSize(2);
        assertThat(visible).allSatisfy(unit ->
            assertThat(unit.path("name").asText()).doesNotContain("Punjab"));
    }

    @Test
    void organizationGrantReachesTheWholeSubtree() {
        ApiClient.Session admin = api.registerTenant("subtree@example.com", "Subtree Federation");
        UUID rootId = rootUnitId(admin);

        UUID stateId = api.post("/api/v1/organization-units", Map.of(
            "parentOrganizationUnitId", rootId,
            "name", "Deep State Association",
            "type", "STATE_ASSOCIATION"
        ), admin.accessToken()).id();

        UUID districtId = api.post("/api/v1/organization-units", Map.of(
            "parentOrganizationUnitId", stateId,
            "name", "Deep District Association",
            "type", "DISTRICT_ASSOCIATION"
        ), admin.accessToken()).id();

        // The grant is on the root only; the district is two levels down and still readable.
        assertThat(api.get("/api/v1/organization-units/" + districtId, admin.accessToken()).status()).isEqualTo(200);
    }

    @Test
    void grantIsScopedToASubtreeAndNotItsParent() {
        ApiClient.Session admin = api.registerTenant("scoped@example.com", "Scoped Federation");
        UUID rootId = rootUnitId(admin);

        UUID stateId = api.post("/api/v1/organization-units", Map.of(
            "parentOrganizationUnitId", rootId,
            "name", "Branch State Association",
            "type", "STATE_ASSOCIATION"
        ), admin.accessToken()).id();

        ApiClient.Session official = inviteAndActivate(admin, "official@example.com", stateId, "ORG_OFFICIAL", stateId);

        assertThat(api.get("/api/v1/organization-units/" + stateId, official.accessToken()).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/organization-units/" + rootId, official.accessToken()).status()).isEqualTo(403);
    }

    @Test
    void orgOfficialCannotInviteOrGrantRoles() {
        ApiClient.Session admin = api.registerTenant("noescalate@example.com", "No Escalation Federation");
        UUID rootId = rootUnitId(admin);

        ApiClient.Session official = inviteAndActivate(admin, "limited@example.com", rootId, "ORG_OFFICIAL", rootId);

        // ORG_OFFICIAL holds neither user:invite nor role:assign.
        assertThat(api.post("/api/v1/users/invite", Map.of(
            "email", "someone-else@example.com",
            "displayName", "Someone Else",
            "organizationUnitId", rootId
        ), official.accessToken()).status()).isEqualTo(403);

        assertThat(api.post("/api/v1/users/" + official.userId() + "/role-assignments", Map.of(
            "roleCode", "TENANT_ADMIN",
            "scopeType", "ORGANIZATION",
            "scopeId", rootId
        ), official.accessToken()).status()).isEqualTo(403);
    }

    @Test
    void adminCannotGrantRolesOutsideOwnScope() {
        ApiClient.Session haryana = api.registerTenant("grant-haryana@example.com", "Grant Haryana Association");
        ApiClient.Session punjab = api.registerTenant("grant-punjab@example.com", "Grant Punjab Association");

        ApiClient.Response forbidden = api.post("/api/v1/users/" + haryana.userId() + "/role-assignments", Map.of(
            "roleCode", "TENANT_ADMIN",
            "scopeType", "ORGANIZATION",
            "scopeId", rootUnitId(punjab)
        ), haryana.accessToken());

        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.errorCode()).isEqualTo("SCOPE_FORBIDDEN");
    }

    @Test
    void nobodyCanCreateANewRootTenantThroughTheApi() {
        ApiClient.Session admin = api.registerTenant("rootmaker@example.com", "Rootmaker Federation");

        ApiClient.Response forbidden = api.post("/api/v1/organization-units", Map.of(
            "name", "Sneaky New Federation",
            "type", "FEDERATION"
        ), admin.accessToken());

        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.errorCode()).isEqualTo("SCOPE_FORBIDDEN");
    }

    @Test
    void aTenantAdminCannotEscalateItselfToGlobalScope() {
        ApiClient.Session admin = api.registerTenant("scopecheck@example.com", "Scopecheck Federation");

        ApiClient.Response invalid = api.post("/api/v1/users/" + admin.userId() + "/role-assignments", Map.of(
            "roleCode", "TENANT_ADMIN",
            "scopeType", "GLOBAL"
        ), admin.accessToken());

        assertThat(invalid.status()).isEqualTo(403);
        assertThat(invalid.errorCode()).isEqualTo("SCOPE_FORBIDDEN");
    }

    @Test
    void duplicateGrantsAreRejected() {
        ApiClient.Session admin = api.registerTenant("dupgrant@example.com", "Dupgrant Federation");
        UUID rootId = rootUnitId(admin);

        ApiClient.Response duplicate = api.post("/api/v1/users/" + admin.userId() + "/role-assignments", Map.of(
            "roleCode", "TENANT_ADMIN",
            "scopeType", "ORGANIZATION",
            "scopeId", rootId
        ), admin.accessToken());

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.errorCode()).isEqualTo("ASSIGNMENT_EXISTS");
    }

    @Test
    void revokingAGrantRemovesTheAccessItConferred() {
        ApiClient.Session admin = api.registerTenant("revoke@example.com", "Revoke Federation");
        UUID rootId = rootUnitId(admin);

        ApiClient.Session official = inviteAndActivate(admin, "revokee@example.com", rootId, "ORG_OFFICIAL", rootId);
        assertThat(api.get("/api/v1/organization-units/" + rootId, official.accessToken()).status()).isEqualTo(200);

        UUID assignmentId = UUID.fromString(
            api.get("/api/v1/users/" + official.userId() + "/role-assignments", admin.accessToken())
                .json().get(0).path("id").asText()
        );

        assertThat(api.delete("/api/v1/users/" + official.userId() + "/role-assignments/" + assignmentId, admin.accessToken()).status())
            .isEqualTo(204);
        assertThat(api.get("/api/v1/organization-units/" + rootId, official.accessToken()).status()).isEqualTo(403);
    }

    @Test
    void invitedUserWithAnInitialRoleGetsItOnAcceptance() {
        ApiClient.Session admin = api.registerTenant("initialrole@example.com", "Initialrole Federation");
        UUID rootId = rootUnitId(admin);

        ApiClient.Session official = inviteAndActivate(admin, "withrole@example.com", rootId, "ORG_OFFICIAL", rootId);

        JsonNode assignments = api.get("/api/v1/users/" + official.userId() + "/role-assignments", official.accessToken()).json();

        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).path("roleCode").asText()).isEqualTo("ORG_OFFICIAL");
    }

    private ApiClient.Session inviteAndActivate(
        ApiClient.Session inviter,
        String email,
        UUID organizationUnitId,
        String roleCode,
        UUID scopeId
    ) {
        String inviteToken = api.post("/api/v1/users/invite", Map.of(
            "email", email,
            "displayName", "Invited User",
            "organizationUnitId", organizationUnitId,
            "initialRole", Map.of("roleCode", roleCode, "scopeType", "ORGANIZATION", "scopeId", scopeId)
        ), inviter.accessToken()).json().path("inviteToken").asText();

        JsonNode accepted = api.post("/api/v1/auth/invite-accept", Map.of(
            "inviteToken", inviteToken,
            "password", "StrongPass123"
        ), null).json();

        return new ApiClient.Session(
            UUID.fromString(accepted.path("user").path("id").asText()),
            accepted.path("accessToken").asText(),
            accepted.path("refreshToken").asText()
        );
    }

    private UUID rootUnitId(ApiClient.Session session) {
        return UUID.fromString(
            api.get("/api/v1/organization-units", session.accessToken()).json().get(0).path("id").asText()
        );
    }
}
