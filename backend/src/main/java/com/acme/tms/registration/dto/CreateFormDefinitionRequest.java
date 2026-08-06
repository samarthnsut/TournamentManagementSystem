package com.acme.tms.registration.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

public record CreateFormDefinitionRequest(@NotNull JsonNode schema) {
}
