package com.acme.tms.tournament.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.tournament.dto.VenueRequest;
import com.acme.tms.tournament.dto.VenueResponse;
import com.acme.tms.tournament.service.VenueService;

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
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(value = "venue:create", scope = ScopeType.ORGANIZATION,
        scopeIdParam = "request.organizationUnitId")
    public VenueResponse create(@Valid @RequestBody VenueRequest request) {
        return venueService.create(request);
    }

    /** Scope-filtered rather than permission-gated, like the organization-unit list. */
    @GetMapping
    public List<VenueResponse> list() {
        return venueService.list();
    }

    @GetMapping("/{id}")
    @RequiresPermission(value = "venue:read", scope = ScopeType.VENUE, scopeIdParam = "id")
    public VenueResponse get(@PathVariable UUID id) {
        return venueService.get(id);
    }

    @PatchMapping("/{id}")
    @RequiresPermission(value = "venue:update", scope = ScopeType.VENUE, scopeIdParam = "id")
    public VenueResponse update(@PathVariable UUID id, @Valid @RequestBody VenueRequest request) {
        return venueService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(value = "venue:delete", scope = ScopeType.VENUE, scopeIdParam = "id")
    public void archive(@PathVariable UUID id) {
        venueService.archive(id);
    }
}
