package com.acme.tms.identity.service;

import com.acme.tms.common.notification.MailProperties;
import com.acme.tms.common.notification.Mailer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Emails the reset link once the token is definitely stored. */
@Component
public class PasswordResetMailListener {

    private final Mailer mailer;
    private final MailProperties properties;

    public PasswordResetMailListener(Mailer mailer, MailProperties properties) {
        this.mailer = mailer;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        String link = properties.link(
            "/reset-password?token=" + URLEncoder.encode(event.token(), StandardCharsets.UTF_8));

        String body = """
            Hello %s,

            Someone asked to reset the password for this account. If that was you, open the link
            below to choose a new one:

            %s

            The link is valid for %d minutes and can be used once.

            If it was not you, you can ignore this email — your password has not changed, and
            nobody can reset it without this link.
            """.formatted(event.fullName(), link, event.validForMinutes());

        mailer.send(event.email(), "Reset your Tekspo Infinity password", body);
    }
}
