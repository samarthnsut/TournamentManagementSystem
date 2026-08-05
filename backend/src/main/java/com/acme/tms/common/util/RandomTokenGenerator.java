package com.acme.tms.common.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class RandomTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomTokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

