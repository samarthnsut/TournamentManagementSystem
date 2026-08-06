package com.acme.tms.tournament.api;

import com.acme.tms.tournament.dto.PublicTournamentResponse;
import com.acme.tms.tournament.service.PublicTournamentService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous. Everything under {@code /api/v1/public/**} is permitted in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicTournamentController {

    private final PublicTournamentService publicTournamentService;

    public PublicTournamentController(PublicTournamentService publicTournamentService) {
        this.publicTournamentService = publicTournamentService;
    }

    @GetMapping("/t/{slug}")
    public PublicTournamentResponse getBySlug(@PathVariable String slug) {
        return publicTournamentService.getBySlug(slug);
    }
}
