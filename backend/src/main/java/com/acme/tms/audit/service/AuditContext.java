package com.acme.tms.audit.service;

import com.acme.tms.common.security.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/** Who is acting and from where, for callers that must not fail when the answer is "nobody". */
@Component
public class AuditContext {

    /**
     * Empty for system actions. Unlike {@code CurrentUser#requireUserId} this never throws — an
     * auto-approval running with no security context is a legitimate thing to record, not an error.
     */
    public Optional<UUID> actorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user.userId());
    }

    /**
     * Empty outside a request — a scheduled job has no client address.
     *
     * <p>{@code X-Forwarded-For} is trusted only for its first entry, and only because this runs
     * behind a load balancer that rewrites it. Exposed directly, the header is caller-controlled
     * and would let anyone write whatever address they liked into the permanent record.
     */
    public Optional<String> ipAddress() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }

        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return first.isEmpty() ? Optional.empty() : Optional.of(truncate(first));
        }

        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? Optional.empty() : Optional.of(truncate(remote));
    }

    /** The column holds 45 characters; a longer value is a malformed header, not an address. */
    private String truncate(String address) {
        return address.length() <= 45 ? address : address.substring(0, 45);
    }
}
