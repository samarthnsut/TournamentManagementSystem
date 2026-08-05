package com.acme.tms.common.util;

import java.text.Normalizer;
import java.util.Locale;

public final class SlugUtil {

    private SlugUtil() {
    }

    public static String from(String value, String fallback) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");

        return normalized.isBlank() ? fallback : normalized;
    }
}

