package com.acme.tms.identity;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerCreatesTenantAndMakesTheRegistrantItsAdmin() {
        ApiClient.Session session = api.registerTenant("founder@example.com", "Founder Federation");

        JsonNode assignments = api.get("/api/v1/users/" + session.userId() + "/role-assignments", session.accessToken()).json();

        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).path("roleCode").asText()).isEqualTo("TENANT_ADMIN");
        assertThat(assignments.get(0).path("scopeType").asText()).isEqualTo("ORGANIZATION");
    }

    @Test
    void loginReturnsWorkingAccessToken() {
        api.registerTenant("login@example.com", "Login Federation");

        ApiClient.Response login = api.post("/api/v1/auth/login", Map.of(
            "email", "login@example.com",
            "password", "StrongPass123"
        ), null);

        assertThat(login.status()).isEqualTo(200);
        String accessToken = login.json().path("accessToken").asText();
        assertThat(api.get("/api/v1/auth/me", accessToken).status()).isEqualTo(200);
    }

    @Test
    void loginRejectsWrongPassword() {
        api.registerTenant("wrongpass@example.com", "Wrongpass Federation");

        ApiClient.Response login = api.post("/api/v1/auth/login", Map.of(
            "email", "wrongpass@example.com",
            "password", "NotThePassword1"
        ), null);

        assertThat(login.status()).isEqualTo(401);
        assertThat(login.errorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void duplicateEmailIsRejected() {
        api.registerTenant("dup@example.com", "First Federation");

        ApiClient.Response second = api.post("/api/v1/auth/register", Map.of(
            "fullName", "Second User",
            "email", "dup@example.com",
            "password", "StrongPass123",
            "organizationName", "Second Federation",
            "organizationType", "FEDERATION"
        ), null);

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.errorCode()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void refreshRotatesTheTokenAndRetiresTheOldOne() {
        ApiClient.Session session = api.registerTenant("refresh@example.com", "Refresh Federation");

        ApiClient.Response refreshed = api.post("/api/v1/auth/refresh", Map.of(
            "refreshToken", session.refreshToken()
        ), null);
        assertThat(refreshed.status()).isEqualTo(200);

        String rotatedToken = refreshed.json().path("refreshToken").asText();
        assertThat(rotatedToken).isNotEqualTo(session.refreshToken());
        assertThat(api.get("/api/v1/auth/me", refreshed.json().path("accessToken").asText()).status()).isEqualTo(200);
    }

    @Test
    void replayingASpentRefreshTokenKillsTheWholeFamily() {
        ApiClient.Session session = api.registerTenant("replay@example.com", "Replay Federation");

        String rotatedToken = api.post("/api/v1/auth/refresh", Map.of("refreshToken", session.refreshToken()), null)
            .json().path("refreshToken").asText();

        ApiClient.Response replay = api.post("/api/v1/auth/refresh", Map.of("refreshToken", session.refreshToken()), null);
        assertThat(replay.status()).isEqualTo(401);

        // The replay is treated as theft, so the token issued to the honest client is revoked too.
        assertThat(api.post("/api/v1/auth/refresh", Map.of("refreshToken", rotatedToken), null).status()).isEqualTo(401);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        ApiClient.Session session = api.registerTenant("logout@example.com", "Logout Federation");

        assertThat(api.post("/api/v1/auth/logout", Map.of("refreshToken", session.refreshToken()), session.accessToken()).status())
            .isEqualTo(204);
        assertThat(api.post("/api/v1/auth/refresh", Map.of("refreshToken", session.refreshToken()), null).status())
            .isEqualTo(401);
    }

    @Test
    void invitedUserCannotLogInUntilTheInviteIsAccepted() {
        ApiClient.Session admin = api.registerTenant("inviter@example.com", "Inviter Federation");
        UUID rootId = UUID.fromString(
            api.get("/api/v1/organization-units", admin.accessToken()).json().get(0).path("id").asText()
        );

        ApiClient.Response invite = api.post("/api/v1/users/invite", Map.of(
            "email", "ravi@example.com",
            "displayName", "Ravi Kumar",
            "organizationUnitId", rootId
        ), admin.accessToken());
        assertThat(invite.status()).isEqualTo(201);
        assertThat(invite.json().path("status").asText()).isEqualTo("INVITED");

        ApiClient.Response earlyLogin = api.post("/api/v1/auth/login", Map.of(
            "email", "ravi@example.com",
            "password", "StrongPass123"
        ), null);
        assertThat(earlyLogin.status()).isEqualTo(403);
        assertThat(earlyLogin.errorCode()).isEqualTo("USER_INVITED");

        ApiClient.Response accepted = api.post("/api/v1/auth/invite-accept", Map.of(
            "inviteToken", invite.json().path("inviteToken").asText(),
            "password", "StrongPass123",
            "displayName", "Ravi Kumar"
        ), null);
        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.json().path("user").path("status").asText()).isEqualTo("ACTIVE");

        assertThat(api.post("/api/v1/auth/login", Map.of(
            "email", "ravi@example.com",
            "password", "StrongPass123"
        ), null).status()).isEqualTo(200);
    }

    @Test
    void anInviteCannotBeAcceptedTwice() {
        ApiClient.Session admin = api.registerTenant("twice@example.com", "Twice Federation");
        UUID rootId = UUID.fromString(
            api.get("/api/v1/organization-units", admin.accessToken()).json().get(0).path("id").asText()
        );

        String inviteToken = api.post("/api/v1/users/invite", Map.of(
            "email", "once@example.com",
            "displayName", "Once Only",
            "organizationUnitId", rootId
        ), admin.accessToken()).json().path("inviteToken").asText();

        Map<String, String> acceptance = Map.of("inviteToken", inviteToken, "password", "StrongPass123");
        assertThat(api.post("/api/v1/auth/invite-accept", acceptance, null).status()).isEqualTo(200);

        ApiClient.Response replay = api.post("/api/v1/auth/invite-accept", acceptance, null);
        assertThat(replay.status()).isEqualTo(404);
    }

    @Test
    void protectedEndpointsRejectMissingAndGarbageTokens() {
        assertThat(api.get("/api/v1/auth/me", null).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/auth/me", "not-a-jwt").status()).isEqualTo(401);
    }
}
