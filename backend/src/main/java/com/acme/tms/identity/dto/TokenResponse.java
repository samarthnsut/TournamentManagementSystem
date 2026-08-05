package com.acme.tms.identity.dto;

public record TokenResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserResponse user
) {
}

