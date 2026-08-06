package com.acme.tms.workflow.repository;

import com.acme.tms.workflow.domain.ApprovalWorkflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, UUID> {

    Optional<ApprovalWorkflow> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ApprovalWorkflow> findByOrganizationUnitIdAndEntityTypeAndIsActiveTrueAndDeletedAtIsNull(
        UUID organizationUnitId, String entityType);

    List<ApprovalWorkflow> findByOrganizationUnitIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
        java.util.Collection<UUID> organizationUnitIds);
}
