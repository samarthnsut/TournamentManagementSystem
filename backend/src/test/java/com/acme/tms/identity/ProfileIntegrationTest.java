package com.acme.tms.identity;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The signed-in user managing their own account. Half of this is security behaviour: an endpoint
 * that acts on "whoever is asking" has to prove it cannot be pointed at anyone else.
 */
class ProfileIntegrationTest extends AbstractIntegrationTest {

    private ApiClient.Session signUp(String email) {
        return api.registerTenant(email, "Profile Federation");
    }

    @Test
    void theProfileShowsWhoYouAreAndWhatYouHold() {
        ApiClient.Session session = signUp("me@example.com");

        ApiClient.Response response = api.get("/api/v1/users/me", session.accessToken());

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = response.json();
        assertThat(body.path("email").asText()).isEqualTo("me@example.com");
        assertThat(body.path("fullName").asText()).isEqualTo("Test User");
        assertThat(body.path("status").asText()).isEqualTo("ACTIVE");
        // A registrant is their organization's TENANT_ADMIN; the profile is where you find that out.
        assertThat(body.path("roles")).isNotEmpty();
        assertThat(body.path("permissions").toString()).contains("tournament:create");
    }

    @Test
    void anAnonymousCallerGetsNothing() {
        ApiClient.Response response = api.get("/api/v1/users/me", null);

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void nameAndPhoneCanBeChanged() {
        ApiClient.Session session = signUp("rename@example.com");

        ApiClient.Response response = api.patch("/api/v1/users/me",
            Map.of("fullName", "Samarth Gulia", "phone", "+91 98765 43210"), session.accessToken());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().path("fullName").asText()).isEqualTo("Samarth Gulia");
        assertThat(response.json().path("phone").asText()).isEqualTo("+91 98765 43210");

        // And it survives a refetch rather than only echoing the request back.
        assertThat(api.get("/api/v1/users/me", session.accessToken()).json().path("fullName").asText())
            .isEqualTo("Samarth Gulia");
    }

    @Test
    void aBlankPhoneClearsItRatherThanStoringEmptyText() {
        ApiClient.Session session = signUp("clearphone@example.com");
        api.patch("/api/v1/users/me", Map.of("fullName", "With Phone", "phone", "9876543210"),
            session.accessToken());

        api.patch("/api/v1/users/me", Map.of("fullName", "With Phone", "phone", ""),
            session.accessToken());

        assertThat(api.get("/api/v1/users/me", session.accessToken()).json().path("phone").isNull())
            .isTrue();
    }

    @Test
    void anEmptyNameIsRejectedWithTheFieldNamed() {
        ApiClient.Session session = signUp("blankname@example.com");

        ApiClient.Response response = api.patch("/api/v1/users/me",
            Map.of("fullName", "  "), session.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().path("errors").toString()).contains("fullName");
    }

    @Test
    void aNonsensePhoneIsRejected() {
        ApiClient.Session session = signUp("badphone@example.com");

        ApiClient.Response response = api.patch("/api/v1/users/me",
            Map.of("fullName", "Someone", "phone", "not a phone"), session.accessToken());

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void changingThePasswordLetsYouSignInWithTheNewOneOnly() {
        ApiClient.Session session = signUp("newpass@example.com");

        ApiClient.Response changed = api.post("/api/v1/users/me/password",
            Map.of("currentPassword", "StrongPass123", "newPassword", "EvenStronger456"),
            session.accessToken());
        assertThat(changed.status()).isEqualTo(204);

        assertThat(api.post("/api/v1/auth/login",
            Map.of("email", "newpass@example.com", "password", "StrongPass123"), null).status())
            .as("the old password stops working")
            .isEqualTo(401);

        assertThat(api.post("/api/v1/auth/login",
            Map.of("email", "newpass@example.com", "password", "EvenStronger456"), null).status())
            .isEqualTo(200);
    }

    @Test
    void theWrongCurrentPasswordIsRefused() {
        ApiClient.Session session = signUp("wrongcurrent@example.com");

        ApiClient.Response response = api.post("/api/v1/users/me/password",
            Map.of("currentPassword", "NotMyPassword", "newPassword", "EvenStronger456"),
            session.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("INVALID_CREDENTIALS");

        // And nothing changed as a result.
        assertThat(api.post("/api/v1/auth/login",
            Map.of("email", "wrongcurrent@example.com", "password", "StrongPass123"), null).status())
            .isEqualTo(200);
    }

    @Test
    void reusingTheSamePasswordIsRefused() {
        ApiClient.Session session = signUp("samepass@example.com");

        ApiClient.Response response = api.post("/api/v1/users/me/password",
            Map.of("currentPassword", "StrongPass123", "newPassword", "StrongPass123"),
            session.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("PASSWORD_UNCHANGED");
    }

    @Test
    void aShortNewPasswordIsRefused() {
        ApiClient.Session session = signUp("shortpass@example.com");

        ApiClient.Response response = api.post("/api/v1/users/me/password",
            Map.of("currentPassword", "StrongPass123", "newPassword", "short"),
            session.accessToken());

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void changingThePasswordEndsEveryExistingSession() {
        // The point of changing a password is usually that somebody else may know the old one.
        // Sessions opened with it have to die, or the change achieved nothing.
        ApiClient.Session session = signUp("sessions@example.com");
        String refreshToken = session.refreshToken();

        api.post("/api/v1/users/me/password",
            Map.of("currentPassword", "StrongPass123", "newPassword", "EvenStronger456"),
            session.accessToken());

        ApiClient.Response refreshed = api.post("/api/v1/auth/refresh",
            Map.of("refreshToken", refreshToken), null);

        assertThat(refreshed.status())
            .as("the refresh token issued before the change is dead")
            .isEqualTo(401);
    }

    @Test
    void oneUsersChangesDoNotTouchAnother() {
        ApiClient.Session mine = signUp("mine@example.com");
        ApiClient.Session theirs = signUp("theirs@example.com");

        api.patch("/api/v1/users/me", Map.of("fullName", "Renamed Me"), mine.accessToken());

        assertThat(api.get("/api/v1/users/me", theirs.accessToken()).json().path("fullName").asText())
            .isEqualTo("Test User");
        assertThat(api.get("/api/v1/users/me", theirs.accessToken()).json().path("email").asText())
            .isEqualTo("theirs@example.com");
    }

    @Test
    void profileChangesLandInTheAuditTrail() {
        ApiClient.Session session = signUp("audited@example.com");
        api.patch("/api/v1/users/me", Map.of("fullName", "Audited Person"), session.accessToken());

        JsonNode rows = api.get("/api/v1/audit-logs?limit=100", session.accessToken()).json();

        assertThat(rows.findValuesAsText("action")).contains("user:update-profile");
    }
}
