package com.acme.tms.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Strips secrets out of a snapshot before it is written down forever.
 *
 * <p>Audit rows are append-only and long-lived, so a credential that reaches one is a credential
 * that cannot be un-leaked. The match is on **substrings of the field name**, not exact names: a
 * snapshot that adds {@code newPasswordHash} tomorrow is redacted without anyone remembering to
 * update this list, which is the only way a deny-list survives contact with a growing codebase.
 */
@Component
public class AuditRedactor {

    /** Lower-cased fragments; any field whose name contains one is replaced. */
    private static final Set<String> SENSITIVE_FRAGMENTS = Set.of(
        "password",
        "passwordhash",
        "token",
        "secret",
        "credential",
        "apikey",
        "privatekey",
        "otp"
    );

    private static final String REDACTED = "[redacted]";

    private final ObjectMapper objectMapper;

    public AuditRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @return the snapshot as redacted JSON, or null when there is nothing to record. A snapshot
     *     that cannot be serialized is recorded as an error marker rather than thrown: losing the
     *     audit row would be worse than losing its detail, and worse still would be failing the
     *     business operation the row describes.
     */
    public String toRedactedJson(Object snapshot) {
        if (snapshot == null) {
            return null;
        }

        try {
            JsonNode tree = objectMapper.valueToTree(snapshot);
            redact(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            return "{\"auditSnapshotError\":\"" + exception.getClass().getSimpleName() + "\"}";
        }
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            // Names are collected before anything is replaced: mutating the node while its own
            // field iterator is open is the kind of thing that works until a Jackson upgrade.
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);

            for (String name : names) {
                if (isSensitive(name)) {
                    object.put(name, REDACTED);
                } else {
                    redact(object.get(name));
                }
            }
            return;
        }

        if (node != null && node.isArray()) {
            node.forEach(this::redact);
        }
    }

    private boolean isSensitive(String fieldName) {
        String lower = fieldName.toLowerCase(Locale.ROOT);
        return SENSITIVE_FRAGMENTS.stream().anyMatch(lower::contains);
    }
}
