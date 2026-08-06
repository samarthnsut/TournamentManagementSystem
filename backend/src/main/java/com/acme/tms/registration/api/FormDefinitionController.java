package com.acme.tms.registration.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.registration.dto.CreateFormDefinitionRequest;
import com.acme.tms.registration.dto.FormDefinitionResponse;
import com.acme.tms.registration.service.FormDefinitionService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FormDefinitionController {

    private final FormDefinitionService formDefinitionService;

    public FormDefinitionController(FormDefinitionService formDefinitionService) {
        this.formDefinitionService = formDefinitionService;
    }

    @PostMapping("/competitions/{competitionId}/form-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(value = "form:create", scope = ScopeType.COMPETITION, scopeIdParam = "competitionId")
    public FormDefinitionResponse publish(
        @PathVariable UUID competitionId,
        @Valid @RequestBody CreateFormDefinitionRequest request
    ) {
        return formDefinitionService.publish(competitionId, request.schema());
    }

    @GetMapping("/competitions/{competitionId}/form-definitions")
    @RequiresPermission(value = "form:read", scope = ScopeType.COMPETITION, scopeIdParam = "competitionId")
    public List<FormDefinitionResponse> list(@PathVariable UUID competitionId) {
        return formDefinitionService.list(competitionId);
    }

    /** The version a new submission will be validated against. */
    @GetMapping("/competitions/{competitionId}/form-definitions/active")
    @RequiresPermission(value = "form:read", scope = ScopeType.COMPETITION, scopeIdParam = "competitionId")
    public FormDefinitionResponse getActive(@PathVariable UUID competitionId) {
        return formDefinitionService.getActive(competitionId);
    }

    @GetMapping("/form-definitions/{id}")
    public FormDefinitionResponse get(@PathVariable UUID id) {
        return formDefinitionService.get(id);
    }

    @PutMapping("/form-definitions/{id}")
    public FormDefinitionResponse replaceSchema(
        @PathVariable UUID id,
        @Valid @RequestBody CreateFormDefinitionRequest request
    ) {
        return formDefinitionService.replaceSchema(id, request.schema());
    }
}
