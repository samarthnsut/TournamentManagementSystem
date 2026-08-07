package com.acme.tms.audit;

import com.acme.tms.audit.service.AuditRedactor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit rows are append-only and long-lived, so a credential that reaches one cannot be un-leaked.
 * This is the second line of defence behind snapshots that simply do not carry secrets.
 */
class AuditRedactorTest {

    private final AuditRedactor redactor = new AuditRedactor(new ObjectMapper());

    @ParameterizedTest(name = "{0} is redacted")
    @ValueSource(strings = {
        "password", "passwordHash", "inviteToken", "refreshToken", "accessToken",
        "clientSecret", "apiKey", "privateKey", "otpCode", "PASSWORD", "userCredential",
    })
    void anyFieldWhoseNameLooksSensitiveIsReplaced(String field) {
        String json = redactor.toRedactedJson(Map.of(field, "s3cret-value"));

        assertThat(json).doesNotContain("s3cret-value");
        assertThat(json).contains("[redacted]");
    }

    @Test
    void ordinaryFieldsSurviveIntact() {
        String json = redactor.toRedactedJson(Map.of("name", "Sonipat Strikers", "played", 5));

        assertThat(json).contains("Sonipat Strikers").contains("5");
    }

    @Test
    void secretsAreFoundInsideNestedObjects() {
        // A snapshot is a tree; redacting only the top level would be security theatre.
        String json = redactor.toRedactedJson(
            Map.of("user", Map.of("email", "a@b.com", "passwordHash", "$2a$hash")));

        assertThat(json).doesNotContain("$2a$hash");
        assertThat(json).contains("a@b.com");
    }

    @Test
    void secretsAreFoundInsideArrays() {
        String json = redactor.toRedactedJson(
            Map.of("sessions", List.of(Map.of("refreshToken", "rt-abc"), Map.of("refreshToken", "rt-def"))));

        assertThat(json).doesNotContain("rt-abc").doesNotContain("rt-def");
    }

    @Test
    void nothingToRecordStaysNull() {
        assertThat(redactor.toRedactedJson(null)).isNull();
    }

    @Test
    void aSnapshotThatCannotBeSerializedBecomesAMarkerRatherThanAnException() {
        // Losing the audit row would be worse than losing its detail, and failing the business
        // operation it describes would be worse still.
        Object unserializable = new Object() {
            @SuppressWarnings("unused")
            public String getBoom() {
                throw new IllegalStateException("nope");
            }
        };

        assertThat(redactor.toRedactedJson(unserializable)).contains("auditSnapshotError");
    }
}
