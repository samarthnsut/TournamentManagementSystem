package com.acme.tms.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The fallback when mail is switched off: writes the message to the log instead of sending it.
 *
 * <p>This is the "logged link" the Sprint 1 roadmap entry describes. It keeps the invite flow
 * end-to-end usable on a laptop with no SMTP server, and keeps tests from needing one.
 */
@Component
// Mirrors SmtpMailer's condition rather than using @ConditionalOnMissingBean: component-scan order
// is not guaranteed, so a missing-bean condition on a scanned class can match or not depending on
// classpath ordering. Phrased this way, exactly one of the two matches for any value.
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailer.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("Mail is disabled. Would have sent to {}:\n  Subject: {}\n{}", to, subject, body);
    }
}
