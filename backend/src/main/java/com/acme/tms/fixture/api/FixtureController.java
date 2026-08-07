package com.acme.tms.fixture.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.fixture.dto.FixtureSetResponse;
import com.acme.tms.fixture.dto.GenerateFixturesRequest;
import com.acme.tms.fixture.dto.MatchResponse;
import com.acme.tms.fixture.dto.RegenerateFixturesRequest;
import com.acme.tms.fixture.service.FixtureService;
import com.acme.tms.fixture.service.MatchService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Fixtures are always addressed through their competition (08 section 11). */
@RestController
@RequestMapping("/api/v1/competitions/{competitionId}")
public class FixtureController {

    private final FixtureService fixtureService;
    private final MatchService matchService;

    public FixtureController(FixtureService fixtureService, MatchService matchService) {
        this.fixtureService = fixtureService;
        this.matchService = matchService;
    }

    @PostMapping("/fixtures/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(value = "fixture:generate", scope = ScopeType.COMPETITION, scopeIdParam = "competitionId")
    public FixtureSetResponse generate(
        @PathVariable UUID competitionId,
        @Valid @RequestBody(required = false) GenerateFixturesRequest request
    ) {
        return fixtureService.generate(
            competitionId,
            request == null ? new GenerateFixturesRequest(null, null) : request
        );
    }

    @PostMapping("/fixtures/regenerate")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(value = "fixture:generate", scope = ScopeType.COMPETITION, scopeIdParam = "competitionId")
    public FixtureSetResponse regenerate(
        @PathVariable UUID competitionId,
        @Valid @RequestBody RegenerateFixturesRequest request
    ) {
        return fixtureService.regenerate(competitionId, request);
    }

    @GetMapping("/fixtures")
    @RequiresPermission(value = "fixture:read", scope = ScopeType.COMPETITION, scopeIdParam = "competitionId")
    public FixtureSetResponse get(@PathVariable UUID competitionId) {
        return fixtureService.get(competitionId);
    }

    @GetMapping("/matches")
    @RequiresPermission(value = "match:read", scope = ScopeType.COMPETITION, scopeIdParam = "competitionId")
    public List<MatchResponse> listMatches(@PathVariable UUID competitionId) {
        return matchService.listForCompetition(competitionId);
    }
}
