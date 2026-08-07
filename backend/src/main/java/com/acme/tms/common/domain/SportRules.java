package com.acme.tms.common.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable typed view over a SportConfiguration's {@code rules} object (06 section 4).
 *
 * <p>Strategies read their parameters through this rather than touching {@link JsonNode} directly,
 * so a rule that a tenant left out reads as its documented default instead of a
 * {@code NullPointerException} halfway through generating a season's fixtures. Keys a strategy
 * genuinely cannot work without are declared in {@code requiredRuleKeys()} and rejected at
 * configuration save, long before this class is asked for them.
 */
public final class SportRules {

    private final JsonNode rules;

    private SportRules(JsonNode rules) {
        this.rules = rules == null || rules.isNull() ? MissingNode.getInstance() : rules;
    }

    public static SportRules of(JsonNode rules) {
        return new SportRules(rules);
    }

    /** For unit tests and the no-rules case; every accessor returns its fallback. */
    public static SportRules empty() {
        return new SportRules(MissingNode.getInstance());
    }

    public int getInt(String key, int fallback) {
        JsonNode value = rules.path(key);
        return value.isNumber() ? value.asInt() : fallback;
    }

    public BigDecimal getDecimal(String key, BigDecimal fallback) {
        JsonNode value = rules.path(key);
        return value.isNumber() ? value.decimalValue() : fallback;
    }

    public String getString(String key, String fallback) {
        JsonNode value = rules.path(key);
        return value.isTextual() ? value.asText() : fallback;
    }

    public boolean getBoolean(String key, boolean fallback) {
        JsonNode value = rules.path(key);
        return value.isBoolean() ? value.asBoolean() : fallback;
    }

    /** Ordered list — tiebreaker precedence depends on it, so insertion order is preserved. */
    public List<String> getStringList(String key, List<String> fallback) {
        JsonNode value = rules.path(key);
        if (!value.isArray()) {
            return fallback;
        }

        List<String> items = new ArrayList<>(value.size());
        value.forEach(element -> {
            if (element.isTextual()) {
                items.add(element.asText());
            }
        });
        return List.copyOf(items);
    }

    public boolean has(String key) {
        return rules.hasNonNull(key);
    }
}
