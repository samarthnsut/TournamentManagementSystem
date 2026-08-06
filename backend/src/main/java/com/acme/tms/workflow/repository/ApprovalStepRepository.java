package com.acme.tms.workflow.repository;

import com.acme.tms.workflow.domain.ApprovalStep;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {

    List<ApprovalStep> findByWorkflowIdAndDeletedAtIsNullOrderByLevelAsc(UUID workflowId);

    void deleteByWorkflowId(UUID workflowId);
}
