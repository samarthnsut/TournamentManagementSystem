package com.acme.tms.identity;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.common.notification.Mailer;
import com.acme.tms.support.ApiClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forgotten-password recovery.
 *
 * <p>The behaviour that matters most here is what the endpoint refuses to reveal: an anonymous
 * caller must not be able to tell a registered address from an unregistered one.
 */
@Import(PasswordResetIntegrationTest.CapturingMailConfig.class)
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    record SentMail(String to, String subject, String body) {
    }

    @TestConfiguration
    static class CapturingMailConfig {

        static final List<SentMail> SENT = new ArrayList<>();

        @Bean
        @Primary
        Mailer capturingMailer() {
            return (to, subject, body) -> SENT.add(new SentMail(to, subject, body));
        }
    }

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)");

    @BeforeEach
    void clearMailbox() {
        CapturingMailConfig.SENT.clear();
    }

    private String tokenFromLastEmail() {
        Matcher matcher = TOKEN_IN_LINK.matcher(CapturingMailConfig.SENT.get(0).body());
        assertThat(matcher.find()).as("the email contains a reset link").isTrue();
        return matcher.group(1);
    }

    private ApiClient.Response forgot(String email) {
        return api.post("/api/v1/auth/forgot-password", Map.of("email", email), null);
    }

    @Test
    void aKnownAddressIsEmailedAWorkingLink() {
        api.registerTenant("forgot@example.com", "Reset Federation");

        assertThat(forgot("forgot@example.com").status()).isEqualTo(204);

        assertThat(CapturingMailConfig.SENT).hasSize(1);
        assertThat(CapturingMailConfig.SENT.get(0).to()).isEqualTo("forgot@example.com");
        assertThat(CapturingMailConfig.SENT.get(0).subject()).containsIgnoringCase("reset");
    }

    @Test
    void anUnknownAddressIsAnsweredIdenticallyAndEmailsNobody() {
        // The whole point: no distinct status, no distinct body, nothing to enumerate accounts with.
        ApiClient.Response response = forgot("nobody@example.com");

        assertThat(response.status()).isEqualTo(204);
        assertThat(CapturingMailConfig.SENT).isEmpty();
    }

    @Test
    void anInvitedButUnacceptedAccountCannotBeResetIntoExistence() {
        // Such a user has no password to reset; they finish their invite instead. Allowing a reset
        // would be a second, parallel way to take over a pending invite.
        ApiClient.Session admin = api.registerTenant("inviteonly@example.com", "Reset Federation");
        String unit = api.get("/api/v1/organization-units", admin.accessToken()).json().get(0)
            .path("id").asText();
        api.post("/api/v1/users/invite", Map.of(
            "email", "pending@example.com",
            "displayName", "Pending Person",
            "organizationUnitId", unit), admin.accessToken());
        CapturingMailConfig.SENT.clear();

        assertThat(forgot("pending@example.com").status()).isEqualTo(204);
        assertThat(CapturingMailConfig.SENT).isEmpty();
    }

    @Test
    void theLinkSetsANewPasswordThatWorks() {
        api.registerTenant("resetme@example.com", "Reset Federation");
        forgot("resetme@example.com");

        ApiClient.Response reset = api.post("/api/v1/auth/reset-password",
            Map.of("token", tokenFromLastEmail(), "newPassword", "BrandNewPass1"), null);

        assertThat(reset.status()).isEqualTo(204);
        assertThat(api.post("/api/v1/auth/login",
            Map.of("email", "resetme@example.com", "password", "BrandNewPass1"), null).status())
            .isEqualTo(200);
        assertThat(api.post("/api/v1/auth/login",
            Map.of("email", "resetme@example.com", "password", "StrongPass123"), null).status())
            .as("the old password stops working")
            .isEqualTo(401);
    }

    @Test
    void theLinkWorksOnlyOnce() {
        api.registerTenant("oncereset@example.com", "Reset Federation");
        forgot("oncereset@example.com");
        String token = tokenFromLastEmail();

        api.post("/api/v1/auth/reset-password",
            Map.of("token", token, "newPassword", "BrandNewPass1"), null);
        ApiClient.Response second = api.post("/api/v1/auth/reset-password",
            Map.of("token", token, "newPassword", "YetAnotherPass1"), null);

        assertThat(second.status()).isEqualTo(404);
        assertThat(second.errorCode()).isEqualTo("RESET_TOKEN_NOT_FOUND");
    }

    @Test
    void resettingEndsEverySession() {
        // Someone resetting has usually lost control of the old password; sessions opened with it
        // must not survive.
        ApiClient.Session session = api.registerTenant("killsessions@example.com", "Reset Federation");
        forgot("killsessions@example.com");

        api.post("/api/v1/auth/reset-password",
            Map.of("token", tokenFromLastEmail(), "newPassword", "BrandNewPass1"), null);

        assertThat(api.post("/api/v1/auth/refresh",
            Map.of("refreshToken", session.refreshToken()), null).status())
            .isEqualTo(401);
    }

    @Test
    void anInventedTokenIsRefused() {
        ApiClient.Response response = api.post("/api/v1/auth/reset-password",
            Map.of("token", "made-up-token", "newPassword", "BrandNewPass1"), null);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.errorCode()).isEqualTo("RESET_TOKEN_NOT_FOUND");
    }

    @Test
    void aShortNewPasswordIsRefused() {
        api.registerTenant("shortreset@example.com", "Reset Federation");
        forgot("shortreset@example.com");

        ApiClient.Response response = api.post("/api/v1/auth/reset-password",
            Map.of("token", tokenFromLastEmail(), "newPassword", "short"), null);

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void requestingTwiceInvalidatesTheFirstLink() {
        // The stored hash is overwritten, so only the newest link works — an old email lying around
        // must not still open the account.
        api.registerTenant("twice@example.com", "Reset Federation");
        forgot("twice@example.com");
        String first = tokenFromLastEmail();

        CapturingMailConfig.SENT.clear();
        forgot("twice@example.com");
        String second = tokenFromLastEmail();
        assertThat(second).isNotEqualTo(first);

        assertThat(api.post("/api/v1/auth/reset-password",
            Map.of("token", first, "newPassword", "BrandNewPass1"), null).status())
            .isEqualTo(404);
        assertThat(api.post("/api/v1/auth/reset-password",
            Map.of("token", second, "newPassword", "BrandNewPass1"), null).status())
            .isEqualTo(204);
    }

    @Test
    void theEndpointsNeedNoToken() {
        // A locked-out user has none to give.
        api.registerTenant("anon@example.com", "Reset Federation");

        assertThat(forgot("anon@example.com").status()).isEqualTo(204);
        assertThat(api.post("/api/v1/auth/reset-password",
            Map.of("token", tokenFromLastEmail(), "newPassword", "BrandNewPass1"), null).status())
            .isEqualTo(204);
    }
}
