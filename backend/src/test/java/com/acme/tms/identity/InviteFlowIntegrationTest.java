package com.acme.tms.identity;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.common.notification.MailProperties;
import com.acme.tms.common.notification.Mailer;
import com.acme.tms.support.ApiClient;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invite creation through to the invitee signing in.
 *
 * <p>Mail is captured rather than sent: what matters is that a message goes out, to the right
 * address, containing a link that actually works — not that an SMTP server was reachable.
 */
@Import(InviteFlowIntegrationTest.CapturingMailConfig.class)
class InviteFlowIntegrationTest extends AbstractIntegrationTest {

    record SentMail(String to, String subject, String body) {
    }

    /** Replaces whichever Mailer the profile selected, so the test never depends on that choice. */
    @TestConfiguration
    static class CapturingMailConfig {

        static final List<SentMail> SENT = new ArrayList<>();

        @Bean
        @Primary
        Mailer capturingMailer() {
            return (to, subject, body) -> SENT.add(new SentMail(to, subject, body));
        }
    }

    @Autowired
    private MailProperties mailProperties;

    @BeforeEach
    void clearMailbox() {
        CapturingMailConfig.SENT.clear();
    }

    private ApiClient.Response invite(String token, UUID organizationUnitId, String email) {
        return api.post("/api/v1/users/invite", Map.of(
            "email", email,
            "displayName", "Invited Person",
            "organizationUnitId", organizationUnitId,
            "initialRole", Map.of(
                "roleCode", "ORG_OFFICIAL",
                "scopeType", "ORGANIZATION",
                "scopeId", organizationUnitId)
        ), token);
    }

    private UUID rootUnitOf(ApiClient.Session session) {
        return UUID.fromString(
            api.get("/api/v1/organization-units", session.accessToken()).json().get(0).path("id").asText());
    }

    @Test
    void invitingSomeoneEmailsThemAWorkingLink() {
        ApiClient.Session admin = api.registerTenant("inviter@example.com", "Invite Federation");
        UUID unit = rootUnitOf(admin);

        ApiClient.Response invited = invite(admin.accessToken(), unit, "newcomer@example.com");
        assertThat(invited.status()).isEqualTo(201);

        assertThat(CapturingMailConfig.SENT).hasSize(1);
        SentMail mail = CapturingMailConfig.SENT.get(0);
        assertThat(mail.to()).isEqualTo("newcomer@example.com");
        assertThat(mail.subject()).contains("invited");
        // Names the inviter, so the recipient knows why they got it.
        assertThat(mail.body()).contains("Test User");

        String token = invited.json().path("inviteToken").asText();
        assertThat(mail.body())
            .as("the emailed link carries the same token the API returned")
            .contains(mailProperties.link("/accept-invite?token=" + token));
    }

    @Test
    void theEmailedTokenSignsTheInviteeIn() {
        ApiClient.Session admin = api.registerTenant("flow@example.com", "Invite Federation");
        UUID unit = rootUnitOf(admin);
        String token = invite(admin.accessToken(), unit, "joiner@example.com").json()
            .path("inviteToken").asText();

        ApiClient.Response accepted = api.post("/api/v1/auth/invite-accept", Map.of(
            "inviteToken", token,
            "password", "MyOwnPassword1",
            "displayName", "Joiner"
        ), null);

        assertThat(accepted.status()).isEqualTo(200);
        JsonNode body = accepted.json();
        // A full session, so the UI can drop them straight into the dashboard.
        assertThat(body.path("accessToken").asText()).isNotBlank();
        assertThat(body.path("user").path("email").asText()).isEqualTo("joiner@example.com");
        assertThat(body.path("user").path("status").asText()).isEqualTo("ACTIVE");

        // And the password they chose works from then on.
        assertThat(api.post("/api/v1/auth/login",
            Map.of("email", "joiner@example.com", "password", "MyOwnPassword1"), null).status())
            .isEqualTo(200);
    }

    @Test
    void aSpentInviteIsIndistinguishableFromOneThatNeverExisted() {
        ApiClient.Session admin = api.registerTenant("once@example.com", "Invite Federation");
        String token = invite(admin.accessToken(), rootUnitOf(admin), "single@example.com").json()
            .path("inviteToken").asText();

        api.post("/api/v1/auth/invite-accept",
            Map.of("inviteToken", token, "password", "MyOwnPassword1"), null);

        ApiClient.Response second = api.post("/api/v1/auth/invite-accept",
            Map.of("inviteToken", token, "password", "AnotherPassword1"), null);

        // 404, not "already accepted": accepting clears the stored hash, so a spent token resolves
        // to nothing. That is the better answer — a distinct "already used" would confirm to an
        // anonymous caller that the token was once real. INVITE_ALREADY_ACCEPTED therefore only
        // guards the case of a live hash on a non-INVITED user, which should not arise.
        assertThat(second.status()).isEqualTo(404);
        assertThat(second.errorCode()).isEqualTo("INVITE_NOT_FOUND");
    }

    @Test
    void anInventedTokenIsRefused() {
        ApiClient.Response response = api.post("/api/v1/auth/invite-accept",
            Map.of("inviteToken", "not-a-real-token", "password", "MyOwnPassword1"), null);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.errorCode()).isEqualTo("INVITE_NOT_FOUND");
    }

    @Test
    void aRejectedInviteSendsNoEmail() {
        // The address is already taken, so the invite fails and the transaction rolls back. Nothing
        // may reach an inbox — an AFTER_COMMIT listener is the only way to guarantee that.
        ApiClient.Session admin = api.registerTenant("dupe@example.com", "Invite Federation");
        UUID unit = rootUnitOf(admin);

        ApiClient.Response response = invite(admin.accessToken(), unit, "dupe@example.com");

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(CapturingMailConfig.SENT)
            .as("no mail for an invite that never existed")
            .isEmpty();
    }

    @Test
    void theInviteeStartsWithTheRoleTheyWereGiven() {
        ApiClient.Session admin = api.registerTenant("withrole@example.com", "Invite Federation");
        UUID unit = rootUnitOf(admin);
        String token = invite(admin.accessToken(), unit, "official@example.com").json()
            .path("inviteToken").asText();

        String inviteeToken = api.post("/api/v1/auth/invite-accept",
            Map.of("inviteToken", token, "password", "MyOwnPassword1"), null)
            .json().path("accessToken").asText();

        JsonNode profile = api.get("/api/v1/users/me", inviteeToken).json();
        assertThat(profile.path("roles").get(0).path("roleCode").asText()).isEqualTo("ORG_OFFICIAL");
        // ORG_OFFICIAL can run competitions but not create tournaments.
        assertThat(profile.path("permissions").toString()).contains("result:record");
        assertThat(profile.path("permissions").toString()).doesNotContain("tournament:create");
    }
}
