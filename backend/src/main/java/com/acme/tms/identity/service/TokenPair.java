package com.acme.tms.identity.service;

public record TokenPair(
    String accessToken,
    String refreshToken,
    long expiresIn
) {
}

