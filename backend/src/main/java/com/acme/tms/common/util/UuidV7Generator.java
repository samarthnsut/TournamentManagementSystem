package com.acme.tms.common.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7Generator() {
    }

    public static UUID generate() {
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        long timestamp = Instant.now().toEpochMilli();
        long mostSignificantBits = (timestamp & 0xFFFFFFFFFFFFL) << 16;
        mostSignificantBits |= 0x7000L;
        mostSignificantBits |= randomBytes[0] & 0x0FFFL;

        long leastSignificantBits = 0x8000000000000000L;
        for (int index = 1; index < 9; index++) {
            leastSignificantBits |= (long) (randomBytes[index] & 0xFF) << ((8 - index) * 8);
        }

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}

