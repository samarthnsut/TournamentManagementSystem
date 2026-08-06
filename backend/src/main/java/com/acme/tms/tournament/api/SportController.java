package com.acme.tms.tournament.api;

import com.acme.tms.tournament.dto.SportResponse;
import com.acme.tms.tournament.service.SportConfigurationService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The sport catalog is global reference data with no owning organization unit, so authentication
 * is the only gate — there is no scope to check it against.
 */
@RestController
@RequestMapping("/api/v1/sports")
public class SportController {

    private final SportConfigurationService sportConfigurationService;

    public SportController(SportConfigurationService sportConfigurationService) {
        this.sportConfigurationService = sportConfigurationService;
    }

    @GetMapping
    public List<SportResponse> list() {
        return sportConfigurationService.listSports();
    }
}
