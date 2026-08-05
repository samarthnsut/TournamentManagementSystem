package com.acme.tms.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;
import java.util.UUID;

/** Thin MockMvc wrapper so tests read as API calls rather than request-builder plumbing. */
public class ApiClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public Response post(String path, Object body, String accessToken) {
        return exchange(MockMvcRequestBuilders.post(path).content(json(body)), accessToken);
    }

    public Response get(String path, String accessToken) {
        return exchange(MockMvcRequestBuilders.get(path), accessToken);
    }

    public Response patch(String path, Object body, String accessToken) {
        return exchange(MockMvcRequestBuilders.patch(path).content(json(body)), accessToken);
    }

    public Response delete(String path, String accessToken) {
        return exchange(MockMvcRequestBuilders.delete(path), accessToken);
    }

    /** Registers a tenant; the returned session belongs to its TENANT_ADMIN. */
    public Session registerTenant(String email, String organizationName) {
        JsonNode body = post("/api/v1/auth/register", Map.of(
            "fullName", "Test User",
            "email", email,
            "password", "StrongPass123",
            "organizationName", organizationName,
            "organizationType", "FEDERATION"
        ), null).json();

        return new Session(
            UUID.fromString(body.path("user").path("id").asText()),
            body.path("accessToken").asText(),
            body.path("refreshToken").asText()
        );
    }

    private Response exchange(MockHttpServletRequestBuilder builder, String accessToken) {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        try {
            return new Response(mockMvc.perform(builder).andReturn(), objectMapper);
        } catch (Exception exception) {
            throw new IllegalStateException("Request failed", exception);
        }
    }

    private String json(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize request body", exception);
        }
    }

    public record Session(UUID userId, String accessToken, String refreshToken) {
    }

    public record Response(MvcResult result, ObjectMapper objectMapper) {

        public int status() {
            return result.getResponse().getStatus();
        }

        public JsonNode json() {
            try {
                return objectMapper.readTree(result.getResponse().getContentAsString());
            } catch (Exception exception) {
                throw new IllegalStateException("Response was not JSON", exception);
            }
        }

        public String errorCode() {
            return json().path("code").asText();
        }

        public UUID id() {
            return UUID.fromString(json().path("id").asText());
        }
    }
}
