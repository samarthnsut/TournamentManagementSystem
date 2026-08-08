package com.acme.tms.common.notification;

/**
 * Sends a message, or does not.
 *
 * <p>Deliberately tiny. Delivery is post-MVP (11 section 3.1) and the shape it eventually takes —
 * templates, localisation, a queue — should not leak into the callers now. What callers need today
 * is "tell this person this", and an implementation that cannot throw at them.
 */
public interface Mailer {

    /**
     * Never throws. A failure to send is logged, not propagated: an invite that was created must
     * not be rolled back or reported as failed because a mail server was unreachable, and the
     * token is returned to the inviter regardless.
     */
    void send(String to, String subject, String body);
}
