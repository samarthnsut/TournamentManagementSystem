package com.acme.tms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac-sha256";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatesATokenItJustIssued() {
        JwtService jwtService = serviceWithAccessTokenTtl(900);
        UUID userId = UUID.randomUUID();

        String token = jwtService.issueAccessToken(userId, "user@example.com");

        assertThat(jwtService.validate(token)).hasValue(new AuthenticatedUser(userId, "user@example.com"));
    }

    @Test
    void rejectsAnExpiredToken() {
        JwtService jwtService = serviceWithAccessTokenTtl(0);

        String token = jwtService.issueAccessToken(UUID.randomUUID(), "user@example.com");

        assertThat(jwtService.validate(token)).isEmpty();
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        String foreignToken = new JwtService(
            new JwtProperties("a-completely-different-signing-secret-value", 900, 30),
            objectMapper
        ).issueAccessToken(UUID.randomUUID(), "user@example.com");

        assertThat(serviceWithAccessTokenTtl(900).validate(foreignToken)).isEmpty();
    }

    @Test
    void rejectsATamperedPayload() {
        JwtService jwtService = serviceWithAccessTokenTtl(900);
        String token = jwtService.issueAccessToken(UUID.randomUUID(), "user@example.com");

        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AB." + parts[2];

        assertThat(jwtService.validate(tampered)).isEmpty();
    }

    @Test
    void rejectsMalformedTokens() {
        JwtService jwtService = serviceWithAccessTokenTtl(900);

        assertThat(jwtService.validate("not-a-jwt")).isEmpty();
        assertThat(jwtService.validate("only.two")).isEmpty();
    }

    private JwtService serviceWithAccessTokenTtl(long ttlSeconds) {
        return new JwtService(new JwtProperties(SECRET, ttlSeconds, 30), objectMapper);
    }
}
