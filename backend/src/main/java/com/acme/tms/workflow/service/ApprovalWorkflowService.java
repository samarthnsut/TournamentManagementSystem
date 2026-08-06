package com.acme.tms.workflow.service;

import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.common.security.ScopeEvaluator;
import com.acme.tms.workflow.domain.ApprovalStep;
import com.acme.tms.workflow.domain.ApprovalWorkflow;
import com.acme.tms.workflow.dto.CreateWorkflowRequest;
import com.acme.tms.workflow.dto.WorkflowResponse;
import com.acme.tms.workflow.dto.WorkflowStepRequest;
import com.acme.tms.workflow.dto.WorkflowStepResponse;
import com.acme.tms.workflow.repository.ApprovalStepRepository;
import com.acme.tms.workflow.repository.ApprovalWorkflowRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** CRUD for approval chains. A chain of one level and a chain of three differ only in these rows. */
@Service
public class ApprovalWorkflowService {

    private final ApprovalWorkflowRepository workflowRepository;
    private final ApprovalStepRepository stepRepository;
    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;

    public ApprovalWorkflowService(
        ApprovalWorkflowRepository workflowRepository,
        ApprovalStepRepository stepRepository,
        ScopeEvaluator scopeEvaluator,
        CurrentUser currentUser
    ) {
        this.workflowRepository = workflowRepository;
        this.stepRepository = stepRepository;
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;
    }

    @Transactional
    public WorkflowResponse create(CreateWorkflowRequest request) {
        validateSteps(request.steps());

        // One active chain per organization unit per entity type, so the previous one stands down.
        workflowRepository
            .findByOrganizationUnitIdAndEntityTypeAndIsActiveTrueAndDeletedAtIsNull(
                request.organizationUnitId(), ApprovalInstanceService.ENTITY_REGISTRATION)
            .ifPresent(existing -> existing.setActive(false));
        workflowRepository.flush();

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setOrganizationUnitId(request.organizationUnitId());
        workflow.setWorkflowName(request.workflowName().trim());
        workflow.setEntityType(ApprovalInstanceService.ENTITY_REGISTRATION);
        workflow.setActive(true);
        workflow = workflowRepository.save(workflow);

        for (WorkflowStepRequest step : request.steps()) {
            ApprovalStep entity = new ApprovalStep();
            entity.setWorkflowId(workflow.getId());
            entity.setLevel(step.level());
            entity.setRoleCode(step.roleCode().trim());
            entity.setStepName(step.stepName());
            entity.setApprovalRequired(step.approvalRequired() == null || step.approvalRequired());
            stepRepository.save(entity);
        }

        return toResponse(workflow);
    }

    @Transactional(readOnly = true)
    public WorkflowResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> list() {
        List<UUID> visible =
            scopeEvaluator.visibleOrganizationUnitIds(currentUser.requireUserId(), "organization:read");
        if (visible.isEmpty()) {
            return List.of();
        }
        return workflowRepository.findByOrganizationUnitIdInAndDeletedAtIsNullOrderByCreatedAtDesc(visible)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Deactivating rather than deleting: instances already running keep pointing at their pinned
     * chain, and a decision trail that references a vanished workflow is worse than useless.
     */
    @Transactional
    public void deactivate(UUID id) {
        require(id).setActive(false);
    }

    private void validateSteps(List<WorkflowStepRequest> steps) {
        Set<Integer> levels = new HashSet<>();
        for (WorkflowStepRequest step : steps) {
            if (!levels.add(step.level())) {
                throw new ValidationException(
                    "DUPLICATE_STEP_LEVEL", "Level " + step.level() + " appears more than once.");
            }
        }
        boolean anyRequired = steps.stream()
            .anyMatch(step -> step.approvalRequired() == null || step.approvalRequired());
        if (!anyRequired) {
            throw new ValidationException(
                "NO_ACTIONABLE_STEP",
                "At least one step must require approval, otherwise nothing is ever reviewed."
            );
        }
    }

    public ApprovalWorkflow require(UUID id) {
        return workflowRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("WORKFLOW_NOT_FOUND", "Approval workflow not found."));
    }

    private WorkflowResponse toResponse(ApprovalWorkflow workflow) {
        List<WorkflowStepResponse> steps =
            stepRepository.findByWorkflowIdAndDeletedAtIsNullOrderByLevelAsc(workflow.getId())
                .stream()
                .map(step -> new WorkflowStepResponse(
                    step.getId(), step.getLevel(), step.getRoleCode(),
                    step.getStepName(), step.isApprovalRequired()))
                .toList();

        return new WorkflowResponse(
            workflow.getId(),
            workflow.getOrganizationUnitId(),
            workflow.getWorkflowName(),
            workflow.getEntityType(),
            workflow.isActive(),
            steps,
            workflow.getCreatedAt()
        );
    }
}
