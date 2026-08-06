package com.acme.tms.workflow.service;

import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ScopeAccessDeniedException;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.identity.repository.UserRoleAssignmentRepository;
import com.acme.tms.organization.repository.OrganizationUnitRepository;
import com.acme.tms.workflow.domain.ApprovalAction;
import com.acme.tms.workflow.domain.ApprovalDecision;
import com.acme.tms.workflow.domain.ApprovalInstance;
import com.acme.tms.workflow.domain.ApprovalInstanceStatus;
import com.acme.tms.workflow.domain.ApprovalStep;
import com.acme.tms.workflow.domain.ApprovalWorkflow;
import com.acme.tms.workflow.repository.ApprovalActionRepository;
import com.acme.tms.workflow.repository.ApprovalInstanceRepository;
import com.acme.tms.workflow.repository.ApprovalStepRepository;
import com.acme.tms.workflow.repository.ApprovalWorkflowRepository;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Drives approval instances. All state transitions come from {@link ApprovalEngine}; this class
 * only decides who may act, persists the outcome and reports what happened.
 */
@Service
public class ApprovalInstanceService {

    public static final String ENTITY_REGISTRATION = "REGISTRATION";

    /** The role that acts when a tenant has configured no chain (doc 07 section 7.2). */
    private static final String IMPLICIT_STEP_ROLE = "TOURNAMENT_ADMIN";

    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalActionRepository actionRepository;
    private final ApprovalWorkflowRepository workflowRepository;
    private final ApprovalStepRepository stepRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final CurrentUser currentUser;

    public ApprovalInstanceService(
        ApprovalInstanceRepository instanceRepository,
        ApprovalActionRepository actionRepository,
        ApprovalWorkflowRepository workflowRepository,
        ApprovalStepRepository stepRepository,
        OrganizationUnitRepository organizationUnitRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        CurrentUser currentUser
    ) {
        this.instanceRepository = instanceRepository;
        this.actionRepository = actionRepository;
        this.workflowRepository = workflowRepository;
        this.stepRepository = stepRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.currentUser = currentUser;
    }

    /** What opening an instance concluded, so the caller can set its own entity's status. */
    public record Opened(ApprovalInstance instance, boolean immediatelyApproved) {
    }

    /**
     * Opens a chain for an entity. When every step is notify-only there is nothing to wait for, so
     * the caller is told to treat it as approved outright.
     */
    @Transactional
    public Opened open(String entityType, UUID entityId, UUID organizationUnitId) {
        Optional<ApprovalWorkflow> workflow = resolveWorkflow(entityType, organizationUnitId);
        List<ApprovalEngine.Step> steps = workflow
            .map(found -> toEngineSteps(stepRepository.findByWorkflowIdAndDeletedAtIsNullOrderByLevelAsc(found.getId())))
            .orElseGet(() -> ApprovalEngine.implicitSingleStep(IMPLICIT_STEP_ROLE));

        Optional<Integer> firstLevel = ApprovalEngine.firstActionableLevel(steps);

        ApprovalInstance instance = new ApprovalInstance();
        instance.setOrganizationUnitId(organizationUnitId);
        instance.setWorkflowId(workflow.map(ApprovalWorkflow::getId).orElse(null));
        instance.setEntityType(entityType);
        instance.setEntityId(entityId);
        instance.setCurrentLevel(firstLevel.orElse(1));
        instance.setStatus(firstLevel.isPresent()
            ? ApprovalInstanceStatus.IN_PROGRESS
            : ApprovalInstanceStatus.APPROVED);

        return new Opened(instanceRepository.save(instance), firstLevel.isEmpty());
    }

    /**
     * Records a decision and advances the chain.
     *
     * @return the instance's status after the decision, which the caller maps onto its own entity
     */
    @Transactional
    public ApprovalInstanceStatus act(UUID instanceId, ApprovalDecision decision, String comment) {
        ApprovalInstance instance = require(instanceId);

        if (!instance.getStatus().isOpen()) {
            throw new ConflictException(
                "INSTANCE_NOT_IN_PROGRESS",
                "This approval is already " + instance.getStatus() + "."
            );
        }
        // A rejection without a reason is useless to whoever has to act on it.
        if (decision == ApprovalDecision.REJECT && (comment == null || comment.isBlank())) {
            throw new ValidationException("COMMENT_REQUIRED", "A reason is required when rejecting.");
        }

        List<ApprovalEngine.Step> steps = stepsFor(instance);
        String requiredRole = steps.stream()
            .filter(step -> step.level() == instance.getCurrentLevel())
            .map(ApprovalEngine.Step::roleCode)
            .findFirst()
            .orElse(IMPLICIT_STEP_ROLE);

        UUID actorId = currentUser.requireUserId();
        if (!canAct(actorId, instance, requiredRole)) {
            throw new ScopeAccessDeniedException(
                "NOT_CURRENT_STEP_APPROVER",
                "This step is decided by " + requiredRole + "."
            );
        }

        ApprovalAction action = new ApprovalAction();
        action.setInstanceId(instance.getId());
        action.setStepLevel(instance.getCurrentLevel());
        action.setActorId(actorId);
        action.setDecision(decision);
        action.setComment(comment);
        actionRepository.save(action);

        ApprovalEngine.Outcome outcome =
            ApprovalEngine.apply(steps, instance.getCurrentLevel(), decision);
        instance.setStatus(outcome.status());
        instance.setCurrentLevel(outcome.currentLevel());

        try {
            instanceRepository.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            // Someone else decided this level first; their outcome stands.
            throw new ConflictException(
                "STALE_LEVEL",
                "Another approver already acted on this step."
            );
        }

        return outcome.status();
    }

    /** Withdrawing the subject abandons its chain rather than leaving it open forever. */
    @Transactional
    public void cancelFor(String entityType, UUID entityId) {
        instanceRepository
            .findByEntityTypeAndEntityIdAndStatus(entityType, entityId, ApprovalInstanceStatus.IN_PROGRESS)
            .ifPresent(instance -> instance.setStatus(ApprovalInstanceStatus.CANCELLED));
    }

    @Transactional(readOnly = true)
    public Optional<ApprovalInstance> findOpenFor(String entityType, UUID entityId) {
        return instanceRepository.findByEntityTypeAndEntityIdAndStatus(
            entityType, entityId, ApprovalInstanceStatus.IN_PROGRESS);
    }

    @Transactional(readOnly = true)
    public Optional<ApprovalInstance> findLatestFor(String entityType, UUID entityId) {
        return instanceRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
            .stream()
            .findFirst();
    }

    @Transactional(readOnly = true)
    public List<ApprovalAction> actionsFor(UUID instanceId) {
        return actionRepository.findByInstanceIdOrderByCreatedAtAsc(instanceId);
    }

    /** The step description an approver needs: which level, named by whom it awaits. */
    @Transactional(readOnly = true)
    public Optional<ApprovalEngine.Step> currentStepOf(ApprovalInstance instance) {
        return stepsFor(instance).stream()
            .filter(step -> step.level() == instance.getCurrentLevel())
            .findFirst();
    }

    @Transactional(readOnly = true)
    public int totalStepsOf(ApprovalInstance instance) {
        return (int) stepsFor(instance).stream().filter(ApprovalEngine.Step::approvalRequired).count();
    }

    /** The current step counted among the actionable ones, so "2 of 3" skips notify-only levels. */
    @Transactional(readOnly = true)
    public int positionOf(ApprovalInstance instance) {
        List<Integer> actionable = stepsFor(instance).stream()
            .filter(ApprovalEngine.Step::approvalRequired)
            .map(ApprovalEngine.Step::level)
            .sorted()
            .toList();
        int index = actionable.indexOf(instance.getCurrentLevel());
        return index < 0 ? 1 : index + 1;
    }

    @Transactional(readOnly = true)
    public Optional<String> currentStepNameOf(ApprovalInstance instance) {
        if (instance.getWorkflowId() == null) {
            return Optional.of("approval");
        }
        return stepRepository.findByWorkflowIdAndDeletedAtIsNullOrderByLevelAsc(instance.getWorkflowId())
            .stream()
            .filter(step -> step.getLevel() == instance.getCurrentLevel())
            .map(ApprovalStep::getStepName)
            .filter(name -> name != null && !name.isBlank())
            .findFirst();
    }

    @Transactional(readOnly = true)
    public List<ApprovalInstance> openInstancesIn(List<UUID> organizationUnitIds) {
        return instanceRepository.findByOrganizationUnitIdInAndStatusOrderByCreatedAtAsc(
            organizationUnitIds, ApprovalInstanceStatus.IN_PROGRESS);
    }

    /** Whether this user's roles cover whichever step the instance is currently waiting on. */
    @Transactional(readOnly = true)
    public boolean canActOnCurrentStep(UUID userId, ApprovalInstance instance) {
        String requiredRole = currentStepOf(instance)
            .map(ApprovalEngine.Step::roleCode)
            .orElse(IMPLICIT_STEP_ROLE);
        return canAct(userId, instance, requiredRole);
    }

    public ApprovalInstance require(UUID id) {
        return instanceRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("INSTANCE_NOT_FOUND", "Approval not found."));
    }

    List<ApprovalEngine.Step> stepsFor(ApprovalInstance instance) {
        if (instance.getWorkflowId() == null) {
            return ApprovalEngine.implicitSingleStep(IMPLICIT_STEP_ROLE);
        }
        return toEngineSteps(
            stepRepository.findByWorkflowIdAndDeletedAtIsNullOrderByLevelAsc(instance.getWorkflowId()));
    }

    /** Nearest configured chain wins, so a state body can override its federation's default. */
    private Optional<ApprovalWorkflow> resolveWorkflow(String entityType, UUID organizationUnitId) {
        for (UUID ancestorId : organizationUnitRepository.findAncestorIdsNearestFirst(organizationUnitId)) {
            Optional<ApprovalWorkflow> found = workflowRepository
                .findByOrganizationUnitIdAndEntityTypeAndIsActiveTrueAndDeletedAtIsNull(ancestorId, entityType);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Role check only. The scope check is the caller's, which for registrations is the competition
     * the entry belongs to — the same {@code registration:approve} gate the endpoint already applies.
     */
    private boolean holdsRole(UUID userId, String requiredRole) {
        List<String> held = userRoleAssignmentRepository.findRoleCodes(userId);
        return held.contains(requiredRole) || held.contains("SUPER_ADMIN");
    }

    /**
     * A configured chain names the role that must act, and only that role may. The implicit chain
     * names none — doc 07 calls it "someone still clicks approve" — so the caller's own
     * {@code registration:approve} check at the entity's scope is the whole gate. Demanding a
     * specific role there would lock out the tenant admin who owns the competition.
     */
    private boolean canAct(UUID userId, ApprovalInstance instance, String requiredRole) {
        return instance.getWorkflowId() == null || holdsRole(userId, requiredRole);
    }

    private static List<ApprovalEngine.Step> toEngineSteps(List<ApprovalStep> steps) {
        return steps.stream()
            .map(step -> new ApprovalEngine.Step(step.getLevel(), step.getRoleCode(), step.isApprovalRequired()))
            .toList();
    }
}
