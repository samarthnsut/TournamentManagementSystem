package com.acme.tms.registration.service;

import com.acme.tms.common.audit.Audited;
import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.registration.domain.RegistrationFormDefinition;
import com.acme.tms.registration.dto.FormDefinitionResponse;
import com.acme.tms.registration.repository.RegistrationFormDefinitionRepository;
import com.acme.tms.registration.repository.RegistrationResponseRepository;
import com.acme.tms.tournament.domain.Competition;
import com.acme.tms.tournament.domain.CompetitionStatus;
import com.acme.tms.tournament.service.CompetitionService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Owns the versioning rules for registration forms.
 *
 * <p>The guarantee this exists to protect: an answer set means nothing without the schema it was
 * validated against, so a version that has been answered is frozen forever, and publishing a
 * change always produces a new version rather than mutating the old one.
 */
@Service
public class FormDefinitionService {

    private final RegistrationFormDefinitionRepository formDefinitionRepository;
    private final RegistrationResponseRepository responseRepository;
    private final CompetitionService competitionService;
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public FormDefinitionService(
        RegistrationFormDefinitionRepository formDefinitionRepository,
        RegistrationResponseRepository responseRepository,
        CompetitionService competitionService,
        ObjectMapper objectMapper
    ) {
        this.formDefinitionRepository = formDefinitionRepository;
        this.responseRepository = responseRepository;
        this.competitionService = competitionService;
        this.objectMapper = objectMapper;
    }

    /** Publishes the next version and retires the current one (BR-RFD-1). */
    @Transactional
    @Audited(value = "form:publish", entityType = "Competition", entityIdParam = "competitionId")
    public FormDefinitionResponse publish(UUID competitionId, JsonNode schema) {
        Competition competition = competitionService.require(competitionId);
        requireSchemaIsUsable(schema);
        requireCompetitionAcceptsFormChanges(competition);

        List<RegistrationFormDefinition> existing =
            formDefinitionRepository.findByCompetitionIdAndDeletedAtIsNullOrderByVersionAsc(competitionId);

        // The active version must be stood down before the new one is inserted, or the partial
        // unique index on (competition_id) WHERE is_active would reject the write.
        existing.stream()
            .filter(RegistrationFormDefinition::isActive)
            .forEach(definition -> definition.setActive(false));
        formDefinitionRepository.flush();

        RegistrationFormDefinition definition = new RegistrationFormDefinition();
        definition.setOrganizationUnitId(competition.getOrganizationUnitId());
        definition.setCompetitionId(competitionId);
        definition.setVersion(existing.stream().mapToInt(RegistrationFormDefinition::getVersion).max().orElse(0) + 1);
        definition.setSchema(schema.toString());
        definition.setActive(true);

        return toResponse(formDefinitionRepository.save(definition));
    }

    /**
     * Corrects a version in place. Allowed only while nobody has answered it — once a response
     * points here, changing the schema would rewrite the meaning of a submitted answer (BR-RFD-2).
     */
    @Transactional
    @Audited(value = "form:replace-schema", entityType = "RegistrationFormDefinition", entityIdParam = "formDefinitionId")
    public FormDefinitionResponse replaceSchema(UUID formDefinitionId, JsonNode schema) {
        RegistrationFormDefinition definition = require(formDefinitionId);
        requireSchemaIsUsable(schema);

        if (responseRepository.existsByFormDefinitionId(formDefinitionId)) {
            throw new ConflictException(
                "FORM_DEFINITION_IN_USE",
                "This version has already been answered. Publish a new version instead."
            );
        }

        requireCompetitionAcceptsFormChanges(competitionService.require(definition.getCompetitionId()));

        definition.setSchema(schema.toString());
        return toResponse(definition);
    }

    @Transactional(readOnly = true)
    public FormDefinitionResponse get(UUID formDefinitionId) {
        return toResponse(require(formDefinitionId));
    }

    @Transactional(readOnly = true)
    public FormDefinitionResponse getActive(UUID competitionId) {
        return toResponse(requireActive(competitionId));
    }

    @Transactional(readOnly = true)
    public List<FormDefinitionResponse> list(UUID competitionId) {
        return formDefinitionRepository.findByCompetitionIdAndDeletedAtIsNullOrderByVersionAsc(competitionId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /** The version an incoming submission must be validated against. */
    @Transactional(readOnly = true)
    public RegistrationFormDefinition requireActive(UUID competitionId) {
        return formDefinitionRepository.findByCompetitionIdAndIsActiveTrueAndDeletedAtIsNull(competitionId)
            .orElseThrow(() -> new ConflictException(
                "NO_ACTIVE_FORM_DEFINITION",
                "This competition has no published registration form yet."
            ));
    }

    public RegistrationFormDefinition require(UUID id) {
        return formDefinitionRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "FORM_DEFINITION_NOT_FOUND", "Registration form definition not found."));
    }

    public JsonNode parse(String rawSchema) {
        try {
            return objectMapper.readTree(rawSchema);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored form schema is not valid JSON", exception);
        }
    }

    /** Rejects a schema the validator could never apply, rather than failing at submission time. */
    private void requireSchemaIsUsable(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            throw new ValidationException("INVALID_FORM_SCHEMA", "schema must be a JSON object.");
        }
        try {
            schemaFactory.getSchema(schema);
        } catch (RuntimeException exception) {
            throw new ValidationException(
                "INVALID_FORM_SCHEMA",
                "schema is not a valid JSON Schema: " + exception.getMessage()
            );
        }
    }

    /** Forms are settled once entries are no longer being taken (BR-RFD-3). */
    private void requireCompetitionAcceptsFormChanges(Competition competition) {
        if (competition.getStatus() != CompetitionStatus.DRAFT
            && competition.getStatus() != CompetitionStatus.OPEN) {
            throw new ConflictException(
                "COMPETITION_NOT_EDITABLE",
                "The registration form cannot change once the competition leaves OPEN."
            );
        }
    }

    private FormDefinitionResponse toResponse(RegistrationFormDefinition definition) {
        return new FormDefinitionResponse(
            definition.getId(),
            definition.getCompetitionId(),
            definition.getVersion(),
            parse(definition.getSchema()),
            definition.isActive(),
            definition.getCreatedAt()
        );
    }
}
