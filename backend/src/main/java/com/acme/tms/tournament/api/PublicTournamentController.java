package com.acme.tms.tournament.api;

import com.acme.tms.fixture.dto.PublicFixturesResponse;
import com.acme.tms.fixture.service.PublicFixtureService;
import com.acme.tms.result.dto.LeaderboardResponse;
import com.acme.tms.result.service.LeaderboardService;
import com.acme.tms.tournament.dto.PublicTournamentResponse;
import com.acme.tms.tournament.service.PublicTournamentService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Anonymous. Everything under {@code /api/v1/public/**} is permitted in SecurityConfig, so nothing
 * reachable from here may consult the caller's identity — and every route is addressed by slug, so
 * the tournament's own visibility is always the gate (see {@code PublicAccessService}).
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicTournamentController {

    private final PublicTournamentService publicTournamentService;
    private final PublicFixtureService publicFixtureService;
    private final LeaderboardService leaderboardService;

    public PublicTournamentController(
        PublicTournamentService publicTournamentService,
        PublicFixtureService publicFixtureService,
        LeaderboardService leaderboardService
    ) {
        this.publicTournamentService = publicTournamentService;
        this.publicFixtureService = publicFixtureService;
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/t/{slug}")
    public PublicTournamentResponse getBySlug(@PathVariable String slug) {
        return publicTournamentService.getBySlug(slug);
    }

    @GetMapping("/t/{slug}/competitions/{competitionId}/fixtures")
    public PublicFixturesResponse getFixtures(
        @PathVariable String slug,
        @PathVariable UUID competitionId
    ) {
        return publicFixtureService.getBySlug(slug, competitionId);
    }

    @GetMapping("/t/{slug}/competitions/{competitionId}/leaderboard")
    public LeaderboardResponse getLeaderboard(
        @PathVariable String slug,
        @PathVariable UUID competitionId
    ) {
        return leaderboardService.getBySlug(slug, competitionId);
    }
}
