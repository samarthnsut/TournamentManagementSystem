package com.acme.tms.workflow.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.workflow.dto.CreateWorkflowRequest;
import com.acme.tms.workflow.dto.WorkflowResponse;
import com.acme.tms.workflow.service.ApprovalWorkflowService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/approval-workflows")
public class ApprovalWorkflowController {

    private final ApprovalWorkflowService workflowService;

    public ApprovalWorkflowController(ApprovalWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(value = "role:assign", scope = ScopeType.ORGANIZATION,
        scopeIdParam = "request.organizationUnitId")
    public WorkflowResponse create(@Valid @RequestBody CreateWorkflowRequest request) {
        return workflowService.create(request);
    }

    @GetMapping
    public List<WorkflowResponse> list() {
        return workflowService.list();
    }

    @GetMapping("/{id}")
    public WorkflowResponse get(@PathVariable UUID id) {
        return workflowService.get(id);
    }

    /** Deactivates rather than deletes, so running instances keep their pinned chain. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        workflowService.deactivate(id);
    }
}
