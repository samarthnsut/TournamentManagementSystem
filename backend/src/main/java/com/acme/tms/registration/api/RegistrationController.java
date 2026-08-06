package com.acme.tms.registration.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.registration.dto.RegistrationResponseDto;
import com.acme.tms.registration.dto.SubmitRegistrationRequest;
import com.acme.tms.registration.service.RegistrationService;

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

@RestController
@RequestMapping("/api/v1")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * The competition is named in the body rather than the path, so the scope check happens in the
     * service once the target is known.
     */
    @PostMapping("/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(value = "registration:create", scope = ScopeType.COMPETITION,
        scopeIdParam = "request.competitionId")
    public RegistrationResponseDto submit(@Valid @RequestBody SubmitRegistrationRequest request) {
        return registrationService.submit(request);
    }

    @GetMapping("/registrations/{id}")
    public RegistrationResponseDto get(@PathVariable UUID id) {
        return registrationService.get(id);
    }

    @PostMapping("/registrations/{id}/withdraw")
    public RegistrationResponseDto withdraw(@PathVariable UUID id) {
        return registrationService.withdraw(id);
    }

    @GetMapping("/competitions/{competitionId}/registrations")
    @RequiresPermission(value = "registration:read", scope = ScopeType.COMPETITION, scopeIdParam = "competitionId")
    public List<RegistrationResponseDto> listForCompetition(@PathVariable UUID competitionId) {
        return registrationService.listForCompetition(competitionId);
    }
}
