package com.acme.tms.registration.service;

import com.acme.tms.common.exception.ValidationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.TreeSet;

/**
 * Checks submitted answers against the form version they are being filed under.
 *
 * <p>Server-side validation is authoritative (BR-RR-1) — the browser rendering the form is a
 * convenience, and a client that skips it must not be able to store answers that do not match the
 * schema anyone will later read them against.
 */
@Service
public class AnswerValidator {

    private final JsonSchemaFactory schemaFactory =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public void validate(JsonNode answers, JsonNode schema) {
        if (answers == null || !answers.isObject()) {
            throw new ValidationException("INVALID_ANSWERS", "answers must be a JSON object.");
        }

        Set<ValidationMessage> failures = schemaFactory.getSchema(schema).validate(answers);
        if (failures.isEmpty()) {
            return;
        }

        // Report every problem at once; fixing a form one error per round trip is miserable.
        Set<String> messages = new TreeSet<>();
        failures.forEach(failure -> messages.add(failure.getMessage()));
        throw new ValidationException("INVALID_ANSWERS", String.join("; ", messages));
    }
}
