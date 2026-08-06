package com.acme.tms.tournament.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.tournament.dto.CreateSportConfigurationRequest;
import com.acme.tms.tournament.dto.SportConfigurationResponse;
import com.acme.tms.tournament.service.SportConfigurationService;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sport-configurations")
public class SportConfigurationController {

    private final SportConfigurationService sportConfigurationService;

    public SportConfigurationController(SportConfigurationService sportConfigurationService) {
        this.sportConfigurationService = sportConfigurationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(value = "sport-config:create", scope = ScopeType.ORGANIZATION,
        scopeIdParam = "request.organizationUnitId")
    public SportConfigurationResponse create(@Valid @RequestBody CreateSportConfigurationRequest request) {
        return sportConfigurationService.create(request);
    }

    @GetMapping
    public List<SportConfigurationResponse> list(@RequestParam(required = false) UUID sportId) {
        return sportConfigurationService.list(sportId);
    }

    @GetMapping("/{id}")
    public SportConfigurationResponse get(@PathVariable UUID id) {
        return sportConfigurationService.get(id);
    }

    @PutMapping("/{id}")
    public SportConfigurationResponse replace(@PathVariable UUID id, @RequestBody JsonNode config) {
        return sportConfigurationService.replace(id, config);
    }
}
