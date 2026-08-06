package com.acme.tms.tournament.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.tournament.domain.TournamentStatus;
import com.acme.tms.tournament.dto.CancelRequest;
import com.acme.tms.tournament.dto.CreateTournamentRequest;
import com.acme.tms.tournament.dto.TournamentResponse;
import com.acme.tms.tournament.dto.TransitionResponse;
import com.acme.tms.tournament.dto.UpdateTournamentRequest;
import com.acme.tms.tournament.service.TournamentService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;

    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(value = "tournament:create", scope = ScopeType.ORGANIZATION,
        scopeIdParam = "request.organizationUnitId")
    public TournamentResponse create(@Valid @RequestBody CreateTournamentRequest request) {
        return tournamentService.create(request);
    }

    /** Listing filters by what the caller can see, so it needs no scope id of its own. */
    @GetMapping
    public List<TournamentResponse> list(@RequestParam(required = false) TournamentStatus status) {
        return tournamentService.list(status);
    }

    @GetMapping("/{id}")
    @RequiresPermission(value = "tournament:read", scope = ScopeType.TOURNAMENT, scopeIdParam = "id")
    public TournamentResponse get(@PathVariable UUID id) {
        return tournamentService.get(id);
    }

    @PatchMapping("/{id}")
    @RequiresPermission(value = "tournament:update", scope = ScopeType.TOURNAMENT, scopeIdParam = "id")
    public TournamentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTournamentRequest request) {
        return tournamentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(value = "tournament:delete", scope = ScopeType.TOURNAMENT, scopeIdParam = "id")
    public void delete(@PathVariable UUID id) {
        tournamentService.delete(id);
    }

    @PostMapping("/{id}/publish")
    @RequiresPermission(value = "tournament:transition", scope = ScopeType.TOURNAMENT, scopeIdParam = "id")
    public TransitionResponse publish(@PathVariable UUID id) {
        return tournamentService.transition(id, TournamentStatus.PUBLISHED);
    }

    @PostMapping("/{id}/open-registration")
    @RequiresPermission(value = "tournament:transition", scope = ScopeType.TOURNAMENT, scopeIdParam = "id")
    public TransitionResponse openRegistration(@PathVariable UUID id) {
        return tournamentService.transition(id, TournamentStatus.REGISTRATION_OPEN);
    }

    @PostMapping("/{id}/close-registration")
    @RequiresPermission(value = "tournament:transition", scope = ScopeType.TOURNAMENT, scopeIdParam = "id")
    public TransitionResponse closeRegistration(@PathVariable UUID id) {
        return tournamentService.transition(id, TournamentStatus.REGISTRATION_CLOSED);
    }

    @PostMapping("/{id}/start")
    @RequiresPermission(value = "tournament:transition", scope = ScopeType.TOURNAMENT, scopeIdParam = "id")
    public TransitionResponse start(@PathVariable UUID id) {
        return tournamentService.transition(id, TournamentStatus.IN_PROGRESS);
    }

    @PostMapping("/{id}/complete")
    @RequiresPermission(value = "tournament:transition", scope = ScopeType.TOURNAMENT, scopeIdParam = "id")
    public TransitionResponse complete(@PathVariable UUID id) {
        return tournamentService.transition(id, TournamentStatus.COMPLETED);
    }

    @PostMapping("/{id}/cancel")
    @RequiresPermission(value = "tournament:transition", scope = ScopeType.TOURNAMENT, scopeIdParam = "id")
    public TransitionResponse cancel(@PathVariable UUID id, @RequestBody(required = false) CancelRequest request) {
        return tournamentService.transition(id, TournamentStatus.CANCELLED);
    }

    @PostMapping("/{id}/archive")
    @RequiresPermission(value = "tournament:transition", scope = ScopeType.TOURNAMENT, scopeIdParam = "id")
    public TransitionResponse archive(@PathVariable UUID id) {
        return tournamentService.transition(id, TournamentStatus.ARCHIVED);
    }
}
