package com.acme.tms.workflow.repository;

import com.acme.tms.workflow.domain.ApprovalInstance;
import com.acme.tms.workflow.domain.ApprovalInstanceStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance, UUID> {

    Optional<ApprovalInstance> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ApprovalInstance> findByEntityTypeAndEntityIdAndStatus(
        String entityType, UUID entityId, ApprovalInstanceStatus status);

    List<ApprovalInstance> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);

    /** The approver work queue, narrowed to organizations the caller can reach. */
    List<ApprovalInstance> findByOrganizationUnitIdInAndStatusOrderByCreatedAtAsc(
        Collection<UUID> organizationUnitIds, ApprovalInstanceStatus status);
}
