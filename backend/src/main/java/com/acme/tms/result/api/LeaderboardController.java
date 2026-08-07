package com.acme.tms.result.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.result.dto.LeaderboardResponse;
import com.acme.tms.result.service.LeaderboardService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/competitions/{competitionId}")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/leaderboard")
    @RequiresPermission(value = "leaderboard:read", scope = ScopeType.COMPETITION, scopeIdParam = "competitionId")
    public LeaderboardResponse get(@PathVariable UUID competitionId) {
        return leaderboardService.get(competitionId);
    }
}
