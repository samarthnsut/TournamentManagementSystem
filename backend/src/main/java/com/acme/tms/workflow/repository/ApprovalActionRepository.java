package com.acme.tms.workflow.repository;

import com.acme.tms.workflow.domain.ApprovalAction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, UUID> {

    List<ApprovalAction> findByInstanceIdOrderByCreatedAtAsc(UUID instanceId);
}
