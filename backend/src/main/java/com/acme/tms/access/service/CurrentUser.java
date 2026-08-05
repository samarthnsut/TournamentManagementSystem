package com.acme.tms.access.service;

import com.acme.tms.common.exception.AuthenticationException;
import com.acme.tms.common.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUser {

    public UUID requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AuthenticationException("UNAUTHENTICATED", "Authentication is required.");
        }
        return user.userId();
    }
}
