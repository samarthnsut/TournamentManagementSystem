package com.acme.tms.common.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email) {
}

