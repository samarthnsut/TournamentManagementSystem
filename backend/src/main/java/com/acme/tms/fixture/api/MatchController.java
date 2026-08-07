package com.acme.tms.fixture.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.fixture.domain.MatchStatus;
import com.acme.tms.fixture.dto.MatchResponse;
import com.acme.tms.fixture.dto.ScheduleMatchRequest;
import com.acme.tms.fixture.dto.ScheduledMatchResponse;
import com.acme.tms.fixture.service.MatchService;
import com.acme.tms.result.dto.RecordResultRequest;
import com.acme.tms.result.dto.RecordResultResponse;
import com.acme.tms.result.service.ResultService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Item operations on a match are flat, as in 08 section 12; scope comes from its competition. */
@RestController
@RequestMapping("/api/v1/matches/{matchId}")
public class MatchController {

    private final MatchService matchService;
    private final ResultService resultService;

    public MatchController(MatchService matchService, ResultService resultService) {
        this.matchService = matchService;
        this.resultService = resultService;
    }

    @GetMapping
    @RequiresPermission(value = "match:read", scope = ScopeType.MATCH, scopeIdParam = "matchId")
    public MatchResponse get(@PathVariable UUID matchId) {
        return matchService.get(matchId);
    }

    @PostMapping("/schedule")
    @RequiresPermission(value = "match:schedule", scope = ScopeType.MATCH, scopeIdParam = "matchId")
    public ScheduledMatchResponse schedule(
        @PathVariable UUID matchId,
        @Valid @RequestBody ScheduleMatchRequest request
    ) {
        return matchService.schedule(matchId, request);
    }

    @PostMapping("/start")
    @RequiresPermission(value = "match:schedule", scope = ScopeType.MATCH, scopeIdParam = "matchId")
    public MatchResponse start(@PathVariable UUID matchId) {
        return matchService.transition(matchId, MatchStatus.LIVE);
    }

    @PostMapping("/postpone")
    @RequiresPermission(value = "match:schedule", scope = ScopeType.MATCH, scopeIdParam = "matchId")
    public MatchResponse postpone(@PathVariable UUID matchId) {
        return matchService.transition(matchId, MatchStatus.POSTPONED);
    }

    @PostMapping("/cancel")
    @RequiresPermission(value = "match:schedule", scope = ScopeType.MATCH, scopeIdParam = "matchId")
    public MatchResponse cancel(@PathVariable UUID matchId) {
        return matchService.transition(matchId, MatchStatus.CANCELLED);
    }

    @PostMapping("/result")
    @RequiresPermission(value = "result:record", scope = ScopeType.MATCH, scopeIdParam = "matchId")
    public RecordResultResponse recordResult(
        @PathVariable UUID matchId,
        @Valid @RequestBody RecordResultRequest request
    ) {
        return resultService.record(matchId, request);
    }
}
