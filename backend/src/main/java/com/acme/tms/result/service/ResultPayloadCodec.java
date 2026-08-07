package com.acme.tms.result.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** The single place result JSONB is written and read, so the two can never disagree on shape. */
@Component
public class ResultPayloadCodec {

    private final ObjectMapper objectMapper;

    public ResultPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(ResultPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Result payload could not be serialized", exception);
        }
    }

    public ResultPayload decode(String json) {
        try {
            return objectMapper.readValue(json, ResultPayload.class);
        } catch (JsonProcessingException exception) {
            // Only reachable if something wrote this column outside the codec.
            throw new IllegalStateException("Stored result payload is unreadable", exception);
        }
    }
}
