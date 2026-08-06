package com.acme.tms.tournament.service;

import com.acme.tms.common.domain.RegistrationApprovalPolicy;
import com.acme.tms.organization.repository.OrganizationUnitRepository;
import com.acme.tms.tournament.domain.Tournament;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Answers "does an entry to this tournament need a human decision?".
 *
 * <p>Nearest setting wins: the tournament's own policy if it has one, otherwise the owning
 * organization unit's. That mirrors how 07_APPROVAL_WORKFLOW_ENGINE resolves workflows, so an
 * organizer learns one rule rather than two.
 */
@Service
public class ApprovalPolicyService {

    private final OrganizationUnitRepository organizationUnitRepository;

    public ApprovalPolicyService(OrganizationUnitRepository organizationUnitRepository) {
        this.organizationUnitRepository = organizationUnitRepository;
    }

    @Transactional(readOnly = true)
    public RegistrationApprovalPolicy resolve(Tournament tournament) {
        if (tournament.getRegistrationApprovalPolicy() != null) {
            return tournament.getRegistrationApprovalPolicy();
        }
        return organizationDefault(tournament.getOrganizationUnitId());
    }

    @Transactional(readOnly = true)
    public RegistrationApprovalPolicy organizationDefault(UUID organizationUnitId) {
        return organizationUnitRepository.findByIdAndDeletedAtIsNull(organizationUnitId)
            .map(unit -> unit.getRegistrationApprovalPolicy())
            // A missing unit should be impossible, but requiring review is the safe way to be wrong.
            .orElse(RegistrationApprovalPolicy.DIRECT_SINGLE_APPROVAL);
    }
}
