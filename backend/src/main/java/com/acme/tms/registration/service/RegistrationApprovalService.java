package com.acme.tms.registration.service;

import com.acme.tms.common.audit.Audited;
import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ScopeAccessDeniedException;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.common.security.ScopeEvaluator;
import com.acme.tms.common.security.ScopeTarget;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.registration.domain.Registration;
import com.acme.tms.registration.domain.RegistrationStatus;
import com.acme.tms.registration.repository.ParticipantRepository;
import com.acme.tms.registration.repository.RegistrationRepository;
import com.acme.tms.workflow.domain.ApprovalDecision;
import com.acme.tms.workflow.domain.ApprovalInstance;
import com.acme.tms.workflow.domain.ApprovalInstanceStatus;
import com.acme.tms.workflow.dto.ApprovalActionResponse;
import com.acme.tms.workflow.dto.ApprovalInstanceResponse;
import com.acme.tms.workflow.service.ApprovalEngine;
import com.acme.tms.workflow.service.ApprovalInstanceService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Joins a registration to its approval chain.
 *
 * <p>Lives in {@code registration} rather than {@code workflow} so the dependency runs one way:
 * registrations know about approvals, approvals stay generic and know nothing about registrations.
 */
@Service
public class RegistrationApprovalService {

    private final RegistrationRepository registrationRepository;
    private final ParticipantRepository participantRepository;
    private final ApprovalInstanceService approvalInstanceService;
    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;

    public RegistrationApprovalService(
        RegistrationRepository registrationRepository,
        ParticipantRepository participantRepository,
        ApprovalInstanceService approvalInstanceService,
        ScopeEvaluator scopeEvaluator,
        CurrentUser currentUser
    ) {
        this.registrationRepository = registrationRepository;
        this.participantRepository = participantRepository;
        this.approvalInstanceService = approvalInstanceService;
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;
    }

    /** One entry awaiting this caller's decision. */
    public record InboxItem(
        UUID registrationId,
        UUID competitionId,
        String participantName,
        int currentLevel,
        int totalLevels,
        String currentStepRole,
        String currentStepName,
        String progressLabel,
        Instant submittedAt
    ) {
    }

    @Transactional
    @Audited(value = "registration:approve", entityType = "Registration", entityIdParam = "registrationId")
    public ApprovalInstanceResponse approve(UUID registrationId, String comment) {
        return decide(registrationId, ApprovalDecision.APPROVE, comment);
    }

    @Transactional
    @Audited(value = "registration:reject", entityType = "Registration", entityIdParam = "registrationId")
    public ApprovalInstanceResponse reject(UUID registrationId, String comment) {
        return decide(registrationId, ApprovalDecision.REJECT, comment);
    }

    private ApprovalInstanceResponse decide(UUID registrationId, ApprovalDecision decision, String comment) {
        Registration registration = requireVisible(registrationId, "registration:approve");

        ApprovalInstance instance = approvalInstanceService
            .findOpenFor(ApprovalInstanceService.ENTITY_REGISTRATION, registrationId)
            .orElseThrow(() -> new ConflictException(
                "NO_OPEN_APPROVAL",
                "This entry has no approval awaiting a decision."
            ));

        ApprovalInstanceStatus status = approvalInstanceService.act(instance.getId(), decision, comment);

        // The registration's own status changes only when the chain reaches a terminal state —
        // "pending at level 2" is workflow state and is never encoded here (BR-REG-5).
        if (status == ApprovalInstanceStatus.APPROVED) {
            registration.setStatus(RegistrationStatus.APPROVED);
            registration.setDecidedAt(Instant.now());
        } else if (status == ApprovalInstanceStatus.REJECTED) {
            registration.setStatus(RegistrationStatus.REJECTED);
            registration.setDecidedAt(Instant.now());
        }

        return describe(approvalInstanceService.require(instance.getId()));
    }

    @Transactional(readOnly = true)
    public ApprovalInstanceResponse forRegistration(UUID registrationId) {
        requireVisible(registrationId, "registration:read");
        return approvalInstanceService
            .findLatestFor(ApprovalInstanceService.ENTITY_REGISTRATION, registrationId)
            .map(this::describe)
            .orElseThrow(() -> new ResourceNotFoundException(
                "NO_APPROVAL", "This entry has no approval record."));
    }

    /**
     * Everything open in an organization the caller can reach, narrowed to steps their roles can
     * actually decide — an inbox listing work you cannot do is worse than an empty one.
     */
    @Transactional(readOnly = true)
    public List<InboxItem> inbox() {
        UUID userId = currentUser.requireUserId();
        List<UUID> visible = scopeEvaluator.visibleOrganizationUnitIds(userId, "registration:approve");
        if (visible.isEmpty()) {
            return List.of();
        }

        List<InboxItem> items = new ArrayList<>();
        for (ApprovalInstance instance : approvalInstanceService.openInstancesIn(visible)) {
            if (!ApprovalInstanceService.ENTITY_REGISTRATION.equals(instance.getEntityType())) {
                continue;
            }
            if (!approvalInstanceService.canActOnCurrentStep(userId, instance)) {
                continue;
            }

            registrationRepository.findByIdAndDeletedAtIsNull(instance.getEntityId()).ifPresent(registration -> {
                String name = participantRepository.findByIdAndDeletedAtIsNull(registration.getParticipantId())
                    .map(participant -> participant.getDisplayName())
                    .orElse("Unknown entrant");
                ApprovalEngine.Step step = approvalInstanceService.currentStepOf(instance).orElse(null);
                int total = approvalInstanceService.totalStepsOf(instance);

                items.add(new InboxItem(
                    registration.getId(),
                    registration.getCompetitionId(),
                    name,
                    instance.getCurrentLevel(),
                    total,
                    step == null ? null : step.roleCode(),
                    approvalInstanceService.currentStepNameOf(instance).orElse(null),
                    progressLabel(instance, total),
                    registration.getSubmittedAt()
                ));
            });
        }
        return items;
    }

    private ApprovalInstanceResponse describe(ApprovalInstance instance) {
        int total = approvalInstanceService.totalStepsOf(instance);
        ApprovalEngine.Step step = approvalInstanceService.currentStepOf(instance).orElse(null);

        List<ApprovalActionResponse> actions = approvalInstanceService.actionsFor(instance.getId())
            .stream()
            .map(action -> new ApprovalActionResponse(
                action.getId(),
                action.getStepLevel(),
                action.getActorId(),
                action.getDecision(),
                action.getComment(),
                action.getCreatedAt()
            ))
            .toList();

        return new ApprovalInstanceResponse(
            instance.getId(),
            instance.getEntityType(),
            instance.getEntityId(),
            instance.getStatus(),
            instance.getCurrentLevel(),
            total,
            approvalInstanceService.currentStepNameOf(instance).orElse(null),
            step == null ? null : step.roleCode(),
            progressLabel(instance, total),
            actions
        );
    }

    /** What the UI shows instead of a bare status: where this is, and how far there is to go. */
    private String progressLabel(ApprovalInstance instance, int totalLevels) {
        return switch (instance.getStatus()) {
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case CANCELLED -> "Withdrawn";
            case IN_PROGRESS -> {
                String stepName = approvalInstanceService.currentStepNameOf(instance).orElse(null);
                int position = approvalInstanceService.positionOf(instance);
                String where = stepName == null ? "approval" : stepName;
                yield totalLevels > 1
                    ? "Awaiting " + where + " (" + position + " of " + totalLevels + ")"
                    : "Awaiting " + where;
            }
        };
    }

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
                "SCOPE_FORBIDDEN", "Missing permission " + permission + " for this registration.");
        }
        return registration;
    }
}
