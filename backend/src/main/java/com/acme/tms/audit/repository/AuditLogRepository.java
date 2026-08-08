package com.acme.tms.audit.repository;

import com.acme.tms.audit.domain.AuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Append and read only.
 *
 * <p>{@code JpaRepository} brings delete methods along with it; that they exist on the interface is
 * a fact of the framework, and the guarantee is enforced instead by {@code AuditImmutabilityTest},
 * which fails if any production code calls one.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByOrganizationUnitIdInOrderByTimestampDesc(
        Collection<UUID> organizationUnitIds, Pageable pageable);

    /**
     * The caller's subtree, plus anything they did themselves.
     *
     * <p>Self-service actions — a profile edit, a password change — belong to a person rather than
     * to a tenant, so they carry no organization unit and a subtree filter alone would hide them
     * from everyone. Your own actions are yours to see, and including them leaks nothing.
     */
    Page<AuditLog> findByOrganizationUnitIdInOrActorIdOrderByTimestampDesc(
        Collection<UUID> organizationUnitIds, UUID actorId, Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, UUID entityId);
}
