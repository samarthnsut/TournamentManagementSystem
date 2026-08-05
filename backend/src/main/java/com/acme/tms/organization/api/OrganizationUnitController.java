package com.acme.tms.organization.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.organization.dto.CreateOrganizationUnitRequest;
import com.acme.tms.organization.dto.OrganizationUnitResponse;
import com.acme.tms.organization.dto.OrganizationUnitTreeResponse;
import com.acme.tms.organization.dto.UpdateOrganizationUnitRequest;
import com.acme.tms.organization.service.OrganizationUnitService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization-units")
public class OrganizationUnitController {

    private final OrganizationUnitService organizationUnitService;

    public OrganizationUnitController(OrganizationUnitService organizationUnitService) {
        this.organizationUnitService = organizationUnitService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationUnitResponse create(@Valid @RequestBody CreateOrganizationUnitRequest request) {
        return organizationUnitService.createScoped(request);
    }

    @GetMapping
    public List<OrganizationUnitResponse> list() {
        return organizationUnitService.listVisible();
    }

    @GetMapping("/{id}")
    @RequiresPermission(value = "organization:read", scope = ScopeType.ORGANIZATION, scopeIdParam = "id")
    public OrganizationUnitResponse get(@PathVariable UUID id) {
        return organizationUnitService.get(id);
    }

    @GetMapping("/{id}/tree")
    @RequiresPermission(value = "organization:read", scope = ScopeType.ORGANIZATION, scopeIdParam = "id")
    public OrganizationUnitTreeResponse tree(@PathVariable UUID id) {
        return organizationUnitService.tree(id);
    }

    @PatchMapping("/{id}")
    @RequiresPermission(value = "organization:update", scope = ScopeType.ORGANIZATION, scopeIdParam = "id")
    public OrganizationUnitResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateOrganizationUnitRequest request) {
        return organizationUnitService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(value = "organization:delete", scope = ScopeType.ORGANIZATION, scopeIdParam = "id")
    public void archive(@PathVariable UUID id) {
        organizationUnitService.archive(id);
    }
}
