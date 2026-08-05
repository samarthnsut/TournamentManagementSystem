package com.acme.tms.access;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The role x endpoint x scope grid. Each case grants exactly one role at the tenant root and asserts
 * what that role can reach, so a permission accidentally added to a seed role fails here.
 */
class PermissionMatrixIntegrationTest extends AbstractIntegrationTest {

    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int NO_CONTENT = 204;
    private static final int FORBIDDEN = 403;

    static Stream<Arguments> matrix() {
        return Stream.of(
            Arguments.of("TENANT_ADMIN", "readUnit", OK),
            Arguments.of("TENANT_ADMIN", "readTree", OK),
            Arguments.of("TENANT_ADMIN", "createChild", CREATED),
            Arguments.of("TENANT_ADMIN", "updateUnit", OK),
            Arguments.of("TENANT_ADMIN", "inviteUser", CREATED),
            Arguments.of("TENANT_ADMIN", "archiveUnit", NO_CONTENT),

            Arguments.of("ORG_OFFICIAL", "readUnit", OK),
            Arguments.of("ORG_OFFICIAL", "readTree", OK),
            Arguments.of("ORG_OFFICIAL", "createChild", FORBIDDEN),
            Arguments.of("ORG_OFFICIAL", "updateUnit", FORBIDDEN),
            Arguments.of("ORG_OFFICIAL", "inviteUser", FORBIDDEN),
            Arguments.of("ORG_OFFICIAL", "archiveUnit", FORBIDDEN),

            Arguments.of("NO_ROLE", "readUnit", FORBIDDEN),
            Arguments.of("NO_ROLE", "readTree", FORBIDDEN),
            Arguments.of("NO_ROLE", "createChild", FORBIDDEN),
            Arguments.of("NO_ROLE", "updateUnit", FORBIDDEN),
            Arguments.of("NO_ROLE", "inviteUser", FORBIDDEN),
            Arguments.of("NO_ROLE", "archiveUnit", FORBIDDEN)
        );
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("matrix")
    void enforcesThePermissionGrid(String roleCode, String action, int expectedStatus) {
        ApiClient.Session admin = api.registerTenant("matrix-admin@example.com", "Matrix Federation");
        UUID rootId = rootUnitId(admin);

        ApiClient.Session subject = roleCode.equals("TENANT_ADMIN")
            ? admin
            : inviteWithRole(admin, roleCode + "@example.com", rootId, roleCode);

        assertThat(perform(action, subject, rootId).status()).isEqualTo(expectedStatus);
    }

    private ApiClient.Response perform(String action, ApiClient.Session session, UUID rootId) {
        return switch (action) {
            case "readUnit" -> api.get("/api/v1/organization-units/" + rootId, session.accessToken());
            case "readTree" -> api.get("/api/v1/organization-units/" + rootId + "/tree", session.accessToken());
            case "createChild" -> api.post("/api/v1/organization-units", Map.of(
                "parentOrganizationUnitId", rootId,
                "name", "Matrix State Association",
                "type", "STATE_ASSOCIATION"
            ), session.accessToken());
            case "updateUnit" -> api.patch("/api/v1/organization-units/" + rootId,
                Map.of("name", "Renamed Federation"), session.accessToken());
            case "inviteUser" -> api.post("/api/v1/users/invite", Map.of(
                "email", "matrix-invitee@example.com",
                "displayName", "Matrix Invitee",
                "organizationUnitId", rootId
            ), session.accessToken());
            case "archiveUnit" -> api.delete("/api/v1/organization-units/" + rootId, session.accessToken());
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    }

    /**
     * {@code NO_ROLE} invites a user with no grant at all — a tenant admin cannot mint the
     * GLOBAL-scoped seed roles, so "authenticated but unroled" is the meaningful baseline here.
     */
    private ApiClient.Session inviteWithRole(ApiClient.Session inviter, String email, UUID rootId, String roleCode) {
        Map<String, Object> invitation = new java.util.HashMap<>(Map.of(
            "email", email,
            "displayName", "Matrix Subject",
            "organizationUnitId", rootId
        ));
        if (!roleCode.equals("NO_ROLE")) {
            invitation.put("initialRole", Map.of("roleCode", roleCode, "scopeType", "ORGANIZATION", "scopeId", rootId));
        }

        ApiClient.Response invite = api.post("/api/v1/users/invite", invitation, inviter.accessToken());

        JsonNode accepted = api.post("/api/v1/auth/invite-accept", Map.of(
            "inviteToken", invite.json().path("inviteToken").asText(),
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
