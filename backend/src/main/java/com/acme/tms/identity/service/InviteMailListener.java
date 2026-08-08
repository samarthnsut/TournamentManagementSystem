package com.acme.tms.identity.service;

import com.acme.tms.common.notification.MailProperties;
import com.acme.tms.common.notification.Mailer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Emails the invite, after the invite is definitely real.
 *
 * <p>{@code AFTER_COMMIT} is the whole point of routing this through an event. Sending inline would
 * mean a rolled-back transaction still put a token in someone's inbox — a link that looks valid and
 * resolves to nothing. It also keeps SMTP latency out of the request's transaction, where it would
 * hold a database connection open for the duration.
 */
@Component
public class InviteMailListener {

    private final Mailer mailer;
    private final MailProperties properties;

    public InviteMailListener(Mailer mailer, MailProperties properties) {
        this.mailer = mailer;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserInvited(UserInvitedEvent event) {
        String link = properties.link(
            "/accept-invite?token=" + URLEncoder.encode(event.inviteToken(), StandardCharsets.UTF_8));

        String invitedBy = event.invitedByName() == null || event.invitedByName().isBlank()
            ? "An organizer"
            : event.invitedByName();

        String body = """
            Hello %s,

            %s has invited you to Tekspo Infinity.

            Open the link below to choose a password and finish setting up your account:

            %s

            The link is valid for 7 days. If you were not expecting this, you can ignore it.
            """.formatted(event.fullName(), invitedBy, link);

        mailer.send(event.email(), "You have been invited to Tekspo Infinity", body);
    }
}
