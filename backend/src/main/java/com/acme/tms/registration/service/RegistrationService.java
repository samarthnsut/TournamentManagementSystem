package com.acme.tms.registration.service;

import com.acme.tms.common.domain.ParticipantType;
import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ScopeAccessDeniedException;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.common.security.ScopeEvaluator;
import com.acme.tms.common.security.ScopeTarget;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.registration.domain.MemberRole;
import com.acme.tms.registration.domain.Participant;
import com.acme.tms.registration.domain.Registration;
import com.acme.tms.registration.domain.RegistrationFormDefinition;
import com.acme.tms.registration.domain.RegistrationResponse;
import com.acme.tms.registration.domain.RegistrationStatus;
import com.acme.tms.registration.domain.TeamMember;
import com.acme.tms.registration.dto.ParticipantRequest;
import com.acme.tms.registration.dto.ParticipantResponse;
import com.acme.tms.registration.dto.RegistrationResponseDto;
import com.acme.tms.registration.dto.SubmitRegistrationRequest;
import com.acme.tms.registration.dto.TeamMemberRequest;
import com.acme.tms.registration.dto.TeamMemberResponse;
import com.acme.tms.registration.repository.ParticipantRepository;
import com.acme.tms.registration.repository.RegistrationRepository;
import com.acme.tms.registration.repository.RegistrationResponseRepository;
import com.acme.tms.registration.repository.TeamMemberRepository;
import com.acme.tms.tournament.domain.Competition;
import com.acme.tms.tournament.domain.CompetitionStatus;
import com.acme.tms.common.domain.RegistrationApprovalPolicy;
import com.acme.tms.tournament.service.ApprovalPolicyService;
import com.acme.tms.tournament.service.CompetitionService;
import com.acme.tms.tournament.service.SportConfigurationService;
import com.acme.tms.tournament.service.TournamentService;
import com.acme.tms.workflow.service.ApprovalInstanceService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final RegistrationResponseRepository responseRepository;
    private final ParticipantRepository participantRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final FormDefinitionService formDefinitionService;
    private final AnswerValidator answerValidator;
    private final CompetitionService competitionService;
    private final SportConfigurationService sportConfigurationService;
    private final TournamentService tournamentService;
    private final ApprovalPolicyService approvalPolicyService;
    private final ApprovalInstanceService approvalInstanceService;
    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public RegistrationService(
        RegistrationRepository registrationRepository,
        RegistrationResponseRepository responseRepository,
        ParticipantRepository participantRepository,
        TeamMemberRepository teamMemberRepository,
        FormDefinitionService formDefinitionService,
        AnswerValidator answerValidator,
        CompetitionService competitionService,
        SportConfigurationService sportConfigurationService,
        TournamentService tournamentService,
        ApprovalPolicyService approvalPolicyService,
        ApprovalInstanceService approvalInstanceService,
        ScopeEvaluator scopeEvaluator,
        CurrentUser currentUser,
        ObjectMapper objectMapper
    ) {
        this.registrationRepository = registrationRepository;
        this.responseRepository = responseRepository;
        this.participantRepository = participantRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.formDefinitionService = formDefinitionService;
        this.answerValidator = answerValidator;
        this.competitionService = competitionService;
        this.sportConfigurationService = sportConfigurationService;
        this.tournamentService = tournamentService;
        this.approvalPolicyService = approvalPolicyService;
        this.approvalInstanceService = approvalInstanceService;
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RegistrationResponseDto submit(SubmitRegistrationRequest request) {
        Competition competition = competitionService.require(request.competitionId());

        // Entries are only taken while the competition is open (BR-REG-1).
        if (competition.getStatus() != CompetitionStatus.OPEN) {
            throw new ConflictException(
                "COMPETITION_NOT_OPEN",
                "This competition is " + competition.getStatus() + " and is not accepting entries."
            );
        }

        // Resolved before anything is written, so a competition with no form rejects cleanly.
        RegistrationFormDefinition formDefinition = formDefinitionService.requireActive(competition.getId());
        ParticipantType expectedType = participantTypeOf(competition);

        Participant participant = request.participantId() != null
            ? requireParticipant(request.participantId())
            : createParticipant(request.participant(), competition, expectedType);

        if (participant.getParticipantType() != expectedType) {
            throw new ValidationException(
                "PARTICIPANT_TYPE_MISMATCH",
                "This competition takes " + expectedType + " entries, not " + participant.getParticipantType() + "."
            );
        }

        if (registrationRepository.existsByCompetitionIdAndParticipantIdAndStatusNotAndDeletedAtIsNull(
            competition.getId(), participant.getId(), RegistrationStatus.WITHDRAWN)) {
            throw new ConflictException(
                "ALREADY_REGISTERED",
                "This participant already has a live registration for the competition."
            );
        }

        // Withdrawn entries free their slot, so they are excluded from the count (BR-REG-4).
        if (competition.getMaxRegistrations() != null) {
            long live = registrationRepository.countByCompetitionIdAndStatusNotAndDeletedAtIsNull(
                competition.getId(), RegistrationStatus.WITHDRAWN);
            if (live >= competition.getMaxRegistrations()) {
                throw new ConflictException(
                    "COMPETITION_FULL",
                    "This competition is limited to " + competition.getMaxRegistrations() + " entries."
                );
            }
        }

        answerValidator.validate(request.answers(), formDefinitionService.parse(formDefinition.getSchema()));

        RegistrationApprovalPolicy policy =
            approvalPolicyService.resolve(tournamentService.require(competition.getTournamentId()));
        Instant now = Instant.now();

        Registration registration = new Registration();
        registration.setOrganizationUnitId(competition.getOrganizationUnitId());
        registration.setCompetitionId(competition.getId());
        registration.setParticipantId(participant.getId());
        registration.setSubmittedAt(now);
        registration.setStatus(RegistrationStatus.PENDING);
        registration = registrationRepository.save(registration);

        // AUTO_APPROVE means nobody is reviewing anything, so no chain is opened at all. Otherwise
        // the engine resolves the nearest configured workflow, falling back to an implicit single
        // step — either way the entry now has an instance and shows up in someone's inbox.
        if (policy == RegistrationApprovalPolicy.AUTO_APPROVE) {
            registration.setStatus(RegistrationStatus.APPROVED);
            registration.setDecidedAt(now);
        } else {
            ApprovalInstanceService.Opened opened = approvalInstanceService.open(
                ApprovalInstanceService.ENTITY_REGISTRATION,
                registration.getId(),
                competition.getOrganizationUnitId()
            );
            // A chain of nothing but notify-only steps has nobody to wait for.
            if (opened.immediatelyApproved()) {
                registration.setStatus(RegistrationStatus.APPROVED);
                registration.setDecidedAt(now);
            }
        }

        RegistrationResponse response = new RegistrationResponse();
        response.setRegistrationId(registration.getId());
        // Pinned deliberately: this answer set belongs to this version forever (BR-RR-2).
        response.setFormDefinitionId(formDefinition.getId());
        response.setAnswers(request.answers().toString());
        response.setSubmittedAt(now);
        responseRepository.save(response);

        return toResponse(registration, participant, response, formDefinition.getVersion());
    }

    @Transactional
    public RegistrationResponseDto withdraw(UUID registrationId) {
        Registration registration = requireVisible(registrationId, "registration:update");

        if (registration.getStatus().isFinal()) {
            throw new ConflictException(
                "ALREADY_FINALIZED",
                "This registration is " + registration.getStatus() + " and cannot be withdrawn."
            );
        }

        registration.setStatus(RegistrationStatus.WITHDRAWN);
        registration.setWithdrawnAt(Instant.now());
        approvalInstanceService.cancelFor(
            ApprovalInstanceService.ENTITY_REGISTRATION, registration.getId());

        return toResponse(registration);
    }

    @Transactional(readOnly = true)
    public RegistrationResponseDto get(UUID registrationId) {
        return toResponse(requireVisible(registrationId, "registration:read"));
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponseDto> listForCompetition(UUID competitionId) {
        return registrationRepository.findByCompetitionIdAndDeletedAtIsNullOrderBySubmittedAtAsc(competitionId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Registrations have no scope type of their own; they are reached through the competition that
     * owns them, which an ORGANIZATION or TOURNAMENT grant already covers.
     */
    private Registration requireVisible(UUID registrationId, String permission) {
        Registration registration = registrationRepository.findByIdAndDeletedAtIsNull(registrationId)
            .orElseThrow(() -> new ResourceNotFoundException("REGISTRATION_NOT_FOUND", "Registration not found."));

        boolean allowed = scopeEvaluator.hasPermission(
            currentUser.requireUserId(),
            permission,
            new ScopeTarget(ScopeType.COMPETITION, registration.getCompetitionId())
        );
        if (!allowed) {
            throw new ScopeAccessDeniedException(
                "SCOPE_FORBIDDEN",
                "Missing permission " + permission + " for this registration."
            );
        }

        return registration;
    }

    private ParticipantType participantTypeOf(Competition competition) {
        return ParticipantType.valueOf(configOf(competition).get("participantType").asText());
    }

    private JsonNode configOf(Competition competition) {
        return parse(sportConfigurationService.require(competition.getSportConfigurationId()).getConfig());
    }

    private Participant createParticipant(
        ParticipantRequest request,
        Competition competition,
        ParticipantType expectedType
    ) {
        if (request == null) {
            throw new ValidationException(
                "PARTICIPANT_REQUIRED",
                "Supply either participantId or participant details."
            );
        }

        Participant participant = new Participant();
        participant.setOrganizationUnitId(competition.getOrganizationUnitId());
        participant.setParticipantType(request.participantType());
        participant.setDisplayName(request.displayName().trim());
        participant.setContactEmail(request.contactEmail());
        participant = participantRepository.save(participant);

        if (request.participantType() == ParticipantType.TEAM) {
            saveRoster(participant, request.members(), competition);
        }

        return participant;
    }

    private void saveRoster(Participant participant, List<TeamMemberRequest> members, Competition competition) {
        List<TeamMemberRequest> roster = members == null ? List.of() : members;

        long captains = roster.stream()
            .filter(member -> member.memberRole() == MemberRole.CAPTAIN)
            .count();
        if (captains > 1) {
            throw new ValidationException("MULTIPLE_CAPTAINS", "A team may have at most one captain.");
        }

        requireRosterSizeAllowed(roster.size(), competition);

        for (TeamMemberRequest request : roster) {
            TeamMember member = new TeamMember();
            member.setParticipantId(participant.getId());
            member.setFullName(request.fullName().trim());
            member.setDateOfBirth(request.dateOfBirth());
            member.setMemberRole(request.memberRole() == null ? MemberRole.PLAYER : request.memberRole());
            member.setJerseyNumber(request.jerseyNumber());
            teamMemberRepository.save(member);
        }
    }

    /** Squad limits are sport configuration, not code — read them from {@code rules.teamSize}. */
    private void requireRosterSizeAllowed(int size, Competition competition) {
        JsonNode rules = configOf(competition).path("rules").path("teamSize");
        if (rules.isMissingNode()) {
            return;
        }

        int min = rules.path("min").asInt(0);
        int max = rules.path("max").asInt(Integer.MAX_VALUE);
        if (size < min || size > max) {
            throw new ValidationException(
                "INVALID_ROSTER_SIZE",
                "This competition requires between " + min + " and " + max + " team members, but got " + size + "."
            );
        }
    }

    private Participant requireParticipant(UUID participantId) {
        return participantRepository.findByIdAndDeletedAtIsNull(participantId)
            .orElseThrow(() -> new ResourceNotFoundException("PARTICIPANT_NOT_FOUND", "Participant not found."));
    }

    private RegistrationResponseDto toResponse(Registration registration) {
        Participant participant = requireParticipant(registration.getParticipantId());
        RegistrationResponse response = responseRepository.findByRegistrationId(registration.getId()).orElse(null);
        int version = response == null
            ? 0
            : formDefinitionService.require(response.getFormDefinitionId()).getVersion();
        return toResponse(registration, participant, response, version);
    }

    private RegistrationResponseDto toResponse(
        Registration registration,
        Participant participant,
        RegistrationResponse response,
        int formVersion
    ) {
        List<TeamMemberResponse> members =
            teamMemberRepository.findByParticipantIdAndDeletedAtIsNullOrderByCreatedAtAsc(participant.getId())
                .stream()
                .map(member -> new TeamMemberResponse(
                    member.getId(),
                    member.getFullName(),
                    member.getDateOfBirth(),
                    member.getMemberRole(),
                    member.getJerseyNumber()
                ))
                .toList();

        return new RegistrationResponseDto(
            registration.getId(),
            registration.getCompetitionId(),
            registration.getStatus(),
            new ParticipantResponse(
                participant.getId(),
                participant.getParticipantType(),
                participant.getDisplayName(),
                participant.getContactEmail(),
                members
            ),
            response == null ? null : response.getFormDefinitionId(),
            formVersion,
            response == null ? null : parse(response.getAnswers()),
            registration.getSubmittedAt(),
            registration.getDecidedAt(),
            registration.getWithdrawnAt()
        );
    }

    private JsonNode parse(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored JSON column is not valid JSON", exception);
        }
    }
}
