package com.acme.tms.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    public JwtService(JwtProperties jwtProperties, ObjectMapper objectMapper) {
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
    }

    public String issueAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        Map<String, Object> header = Map.of(
            "alg", "HS256",
            "typ", "JWT"
        );
        Map<String, Object> payload = Map.of(
            "sub", userId.toString(),
            "email", email,
            "iat", now.getEpochSecond(),
            "exp", now.plusSeconds(jwtProperties.accessTokenTtlSeconds()).getEpochSecond()
        );

        String unsignedToken = base64Json(header) + "." + base64Json(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public Optional<AuthenticatedUser> validate(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }

        String unsignedToken = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
            return Optional.empty();
        }

        try {
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (Instant.now().getEpochSecond() >= payload.path("exp").asLong(0)) {
                return Optional.empty();
            }

            return Optional.of(new AuthenticatedUser(
                UUID.fromString(payload.path("sub").asText()),
                payload.path("email").asText()
            ));
        } catch (IllegalArgumentException | IOException exception) {
            return Optional.empty();
        }
    }

    public long accessTokenTtlSeconds() {
        return jwtProperties.accessTokenTtlSeconds();
    }

    public long refreshTokenTtlDays() {
        return jwtProperties.refreshTokenTtlDays();
    }

    private String base64Json(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize token payload", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign token", exception);
        }
    }

    private boolean constantTimeEquals(String first, String second) {
        return java.security.MessageDigest.isEqual(
            first.getBytes(StandardCharsets.UTF_8),
            second.getBytes(StandardCharsets.UTF_8)
        );
    }
}

