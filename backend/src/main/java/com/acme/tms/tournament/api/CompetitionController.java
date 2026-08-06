package com.acme.tms.tournament.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.tournament.domain.CompetitionStatus;
import com.acme.tms.tournament.dto.CompetitionResponse;
import com.acme.tms.tournament.dto.CreateCompetitionRequest;
import com.acme.tms.tournament.dto.TransitionResponse;
import com.acme.tms.tournament.dto.UpdateCompetitionRequest;
import com.acme.tms.tournament.service.CompetitionService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

/** Nested under the tournament for create and list, flat for item operations (08 section 7). */
@RestController
@RequestMapping("/api/v1")
public class CompetitionController {

    private final CompetitionService competitionService;

    public CompetitionController(CompetitionService competitionService) {
        this.competitionService = competitionService;
    }

    @PostMapping("/tournaments/{tournamentId}/competitions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(value = "competition:create", scope = ScopeType.TOURNAMENT, scopeIdParam = "tournamentId")
    public CompetitionResponse create(
        @PathVariable UUID tournamentId,
        @Valid @RequestBody CreateCompetitionRequest request
    ) {
        return competitionService.create(tournamentId, request);
    }

    @GetMapping("/tournaments/{tournamentId}/competitions")
    @RequiresPermission(value = "competition:read", scope = ScopeType.TOURNAMENT, scopeIdParam = "tournamentId")
    public List<CompetitionResponse> listForTournament(@PathVariable UUID tournamentId) {
        return competitionService.listForTournament(tournamentId);
    }

    @GetMapping("/competitions/{id}")
    @RequiresPermission(value = "competition:read", scope = ScopeType.COMPETITION, scopeIdParam = "id")
    public CompetitionResponse get(@PathVariable UUID id) {
        return competitionService.get(id);
    }

    @PatchMapping("/competitions/{id}")
    @RequiresPermission(value = "competition:update", scope = ScopeType.COMPETITION, scopeIdParam = "id")
    public CompetitionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCompetitionRequest request) {
        return competitionService.update(id, request);
    }

    @PostMapping("/competitions/{id}/open")
    @RequiresPermission(value = "competition:transition", scope = ScopeType.COMPETITION, scopeIdParam = "id")
    public TransitionResponse open(@PathVariable UUID id) {
        return competitionService.transition(id, CompetitionStatus.OPEN);
    }

    @PostMapping("/competitions/{id}/close")
    @RequiresPermission(value = "competition:transition", scope = ScopeType.COMPETITION, scopeIdParam = "id")
    public TransitionResponse close(@PathVariable UUID id) {
        return competitionService.transition(id, CompetitionStatus.CLOSED);
    }

    @PostMapping("/competitions/{id}/start")
    @RequiresPermission(value = "competition:transition", scope = ScopeType.COMPETITION, scopeIdParam = "id")
    public TransitionResponse start(@PathVariable UUID id) {
        return competitionService.transition(id, CompetitionStatus.IN_PROGRESS);
    }

    @PostMapping("/competitions/{id}/complete")
    @RequiresPermission(value = "competition:transition", scope = ScopeType.COMPETITION, scopeIdParam = "id")
    public TransitionResponse complete(@PathVariable UUID id) {
        return competitionService.transition(id, CompetitionStatus.COMPLETED);
    }

    @PostMapping("/competitions/{id}/cancel")
    @RequiresPermission(value = "competition:transition", scope = ScopeType.COMPETITION, scopeIdParam = "id")
    public TransitionResponse cancel(@PathVariable UUID id) {
        return competitionService.transition(id, CompetitionStatus.CANCELLED);
    }
}
