package com.acme.tms.tournament.service;

import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.common.security.ScopeEvaluator;
import com.acme.tms.tournament.domain.SportConfiguration;
import com.acme.tms.tournament.dto.CreateSportConfigurationRequest;
import com.acme.tms.tournament.dto.SportConfigurationResponse;
import com.acme.tms.tournament.dto.SportResponse;
import com.acme.tms.tournament.repository.CompetitionRepository;
import com.acme.tms.tournament.repository.SportConfigurationRepository;
import com.acme.tms.tournament.repository.SportRepository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SportConfigurationService {

    private final SportConfigurationRepository sportConfigurationRepository;
    private final SportRepository sportRepository;
    private final CompetitionRepository competitionRepository;
    private final SportConfigurationValidator validator;
    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;

    public SportConfigurationService(
        SportConfigurationRepository sportConfigurationRepository,
        SportRepository sportRepository,
        CompetitionRepository competitionRepository,
        SportConfigurationValidator validator,
        ScopeEvaluator scopeEvaluator,
        CurrentUser currentUser
    ) {
        this.sportConfigurationRepository = sportConfigurationRepository;
        this.sportRepository = sportRepository;
        this.competitionRepository = competitionRepository;
        this.validator = validator;
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<SportResponse> listSports() {
        return sportRepository.findByDeletedAtIsNullOrderByNameAsc()
            .stream()
            .map(sport -> new SportResponse(sport.getId(), sport.getCode(), sport.getName(), sport.getDescription()))
            .toList();
    }

    @Transactional
    public SportConfigurationResponse create(CreateSportConfigurationRequest request) {
        sportRepository.findByIdAndDeletedAtIsNull(request.sportId())
            .orElseThrow(() -> new ResourceNotFoundException("SPORT_NOT_FOUND", "Sport not found."));

        // Everything the engine relies on is checked here, before a row exists.
        validator.validate(request.config());

        SportConfiguration configuration = new SportConfiguration();
        configuration.setOrganizationUnitId(request.organizationUnitId());
        configuration.setSportId(request.sportId());
        configuration.setConfig(request.config().toString());
        configuration.setVersion(1);
        configuration.setActive(true);

        return toResponse(sportConfigurationRepository.save(configuration));
    }

    @Transactional(readOnly = true)
    public SportConfigurationResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public List<SportConfigurationResponse> list(UUID sportId) {
        List<UUID> visibleUnitIds =
            scopeEvaluator.visibleOrganizationUnitIds(currentUser.requireUserId(), "sport-config:read");
        if (visibleUnitIds.isEmpty()) {
            return List.of();
        }

        List<SportConfiguration> configurations = sportId == null
            ? sportConfigurationRepository
                .findByOrganizationUnitIdInAndDeletedAtIsNullOrderByCreatedAtDesc(visibleUnitIds)
            : sportConfigurationRepository
                .findByOrganizationUnitIdInAndSportIdAndDeletedAtIsNullOrderByCreatedAtDesc(visibleUnitIds, sportId);

        return configurations.stream().map(this::toResponse).toList();
    }

    /** Full replace. Refused once a competition depends on it, since strategies would shift underfoot. */
    @Transactional
    public SportConfigurationResponse replace(UUID id, JsonNode config) {
        SportConfiguration configuration = require(id);

        if (competitionRepository.existsBySportConfigurationIdAndDeletedAtIsNull(id)) {
            throw new ConflictException(
                "CONFIG_IN_USE",
                "This configuration is already used by a competition and can no longer be changed."
            );
        }

        validator.validate(config);
        configuration.setConfig(config.toString());
        configuration.setVersion(configuration.getVersion() + 1);

        return toResponse(configuration);
    }

    public SportConfiguration require(UUID id) {
        return sportConfigurationRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "SPORT_CONFIGURATION_NOT_FOUND", "Sport configuration not found."));
    }

    private SportConfigurationResponse toResponse(SportConfiguration configuration) {
        return new SportConfigurationResponse(
            configuration.getId(),
            configuration.getOrganizationUnitId(),
            configuration.getSportId(),
            validator.parse(configuration.getConfig()),
            configuration.getVersion(),
            configuration.isActive(),
            configuration.getCreatedAt()
        );
    }
}
