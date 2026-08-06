package com.acme.tms.registration;

import com.acme.tms.AbstractIntegrationTest;
import com.acme.tms.support.ApiClient;
import com.acme.tms.support.CompetitionFixture;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Written before the implementation, because 10_DEVELOPMENT_ROADMAP calls form versioning the
 * biggest correctness trap in the project: a bug here silently corrupts historical registrations,
 * and it is not the kind of thing anyone notices until an audit.
 *
 * <p>The invariant under test: a submitted answer set belongs forever to the schema version it was
 * validated against. Editing a form must never retroactively change what an earlier entrant is
 * considered to have answered, nor make their record invalid.
 */
class FormVersioningIntegrationTest extends AbstractIntegrationTest {

    private CompetitionFixture fixture;

    @BeforeEach
    void setUpCompetition() {
        fixture = CompetitionFixture.openIndividualCompetition(api, "forms@example.com");
    }

    /** v1 asks only for a jersey name. */
    private static Map<String, Object> schemaV1() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("jerseyName", Map.of("type", "string", "maxLength", 40));
        return Map.of(
            "type", "object",
            "required", List.of("jerseyName"),
            "properties", properties,
            "additionalProperties", false
        );
    }

    /** v2 additionally demands a coach phone, which no v1 entrant could have supplied. */
    private static Map<String, Object> schemaV2() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("jerseyName", Map.of("type", "string", "maxLength", 40));
        properties.put("coachPhone", Map.of("type", "string", "pattern", "^[0-9]{10}$"));
        return Map.of(
            "type", "object",
            "required", List.of("jerseyName", "coachPhone"),
            "properties", properties,
            "additionalProperties", false
        );
    }

    private UUID publishForm(Map<String, Object> schema) {
        ApiClient.Response created = api.post(
            "/api/v1/competitions/" + fixture.competitionId() + "/form-definitions",
            Map.of("schema", schema),
            fixture.organizerToken()
        );
        assertThat(created.status()).isEqualTo(201);
        return created.id();
    }

    private ApiClient.Response submitRegistration(String displayName, Map<String, Object> answers) {
        return api.post("/api/v1/registrations", Map.of(
            "competitionId", fixture.competitionId(),
            "participant", Map.of("participantType", "INDIVIDUAL", "displayName", displayName),
            "answers", answers
        ), fixture.organizerToken());
    }

    @Test
    void firstPublishedFormIsVersionOneAndActive() {
        UUID formId = publishForm(schemaV1());

        JsonNode form = api.get(
            "/api/v1/competitions/" + fixture.competitionId() + "/form-definitions/active",
            fixture.organizerToken()
        ).json();

        assertThat(form.path("id").asText()).isEqualTo(formId.toString());
        assertThat(form.path("version").asInt()).isEqualTo(1);
        assertThat(form.path("isActive").asBoolean()).isTrue();
    }

    @Test
    void publishingAgainCreatesTheNextVersionAndRetiresThePrevious() {
        UUID first = publishForm(schemaV1());
        UUID second = publishForm(schemaV2());

        JsonNode active = api.get(
            "/api/v1/competitions/" + fixture.competitionId() + "/form-definitions/active",
            fixture.organizerToken()
        ).json();
        assertThat(active.path("id").asText()).isEqualTo(second.toString());
        assertThat(active.path("version").asInt()).isEqualTo(2);

        // The old version still exists and is readable — responses point at it.
        JsonNode previous = api.get("/api/v1/form-definitions/" + first, fixture.organizerToken()).json();
        assertThat(previous.path("version").asInt()).isEqualTo(1);
        assertThat(previous.path("isActive").asBoolean()).isFalse();
    }

    @Test
    void aResponsePinsTheVersionItAnsweredAndSurvivesLaterVersions() {
        UUID v1 = publishForm(schemaV1());

        ApiClient.Response submitted = submitRegistration("Early Bird", Map.of("jerseyName", "EARLY"));
        assertThat(submitted.status()).isEqualTo(201);
        UUID registrationId = submitted.id();

        // The organizer now tightens the form. The earlier entrant never supplied a coach phone.
        publishForm(schemaV2());

        JsonNode registration = api.get("/api/v1/registrations/" + registrationId, fixture.organizerToken()).json();

        assertThat(registration.path("formDefinitionId").asText())
            .as("the response must stay pinned to v1, not silently migrate to v2")
            .isEqualTo(v1.toString());
        assertThat(registration.path("formVersion").asInt()).isEqualTo(1);
        assertThat(registration.path("answers").path("jerseyName").asText()).isEqualTo("EARLY");
        assertThat(registration.path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void answersAreValidatedAgainstWhicheverVersionIsActiveAtSubmission() {
        publishForm(schemaV1());
        // Valid under v1, which does not know about coachPhone.
        assertThat(submitRegistration("Before", Map.of("jerseyName", "BEFORE")).status()).isEqualTo(201);

        publishForm(schemaV2());

        // The same payload is now incomplete, because v2 requires a coach phone.
        ApiClient.Response stale = submitRegistration("After", Map.of("jerseyName", "AFTER"));
        assertThat(stale.status()).isEqualTo(400);
        assertThat(stale.errorCode()).isEqualTo("INVALID_ANSWERS");
        assertThat(stale.json().path("detail").asText()).contains("coachPhone");

        assertThat(submitRegistration("After", Map.of("jerseyName", "AFTER", "coachPhone", "9876543210")).status())
            .isEqualTo(201);
    }

    @Test
    void aVersionThatHasBeenAnsweredCannotBeEdited() {
        UUID v1 = publishForm(schemaV1());
        assertThat(submitRegistration("Locked In", Map.of("jerseyName", "LOCKED")).status()).isEqualTo(201);

        ApiClient.Response edit = api.put(
            "/api/v1/form-definitions/" + v1,
            Map.of("schema", schemaV2()),
            fixture.organizerToken()
        );

        assertThat(edit.status()).isEqualTo(409);
        assertThat(edit.errorCode()).isEqualTo("FORM_DEFINITION_IN_USE");
    }

    @Test
    void anUnansweredVersionCanStillBeCorrected() {
        UUID v1 = publishForm(schemaV1());

        ApiClient.Response edit = api.put(
            "/api/v1/form-definitions/" + v1,
            Map.of("schema", schemaV2()),
            fixture.organizerToken()
        );

        assertThat(edit.status()).isEqualTo(200);
        // Correcting in place is not a new version — it is still v1.
        assertThat(edit.json().path("version").asInt()).isEqualTo(1);
    }

    @Test
    void registrationIsRefusedBeforeAnyFormExists() {
        ApiClient.Response response = submitRegistration("Too Early", Map.of("jerseyName", "EARLY"));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("NO_ACTIVE_FORM_DEFINITION");
    }

    @Test
    void answersOutsideTheSchemaAreRejected() {
        publishForm(schemaV1());

        ApiClient.Response response =
            submitRegistration("Chatty", Map.of("jerseyName", "CHATTY", "favouriteColour", "blue"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("INVALID_ANSWERS");
    }

    @Test
    void everyVersionRemainsListableForTheCompetition() {
        publishForm(schemaV1());
        publishForm(schemaV2());

        JsonNode versions = api.get(
            "/api/v1/competitions/" + fixture.competitionId() + "/form-definitions",
            fixture.organizerToken()
        ).json();

        assertThat(versions).hasSize(2);
        assertThat(versions.findValues("version").stream().map(JsonNode::asInt).toList())
            .containsExactly(1, 2);
    }
}
