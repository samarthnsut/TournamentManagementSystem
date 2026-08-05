package com.acme.tms.organization.service;

import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ScopeAccessDeniedException;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.common.security.ScopeEvaluator;
import com.acme.tms.common.security.ScopeTarget;
import com.acme.tms.common.util.SlugUtil;
import com.acme.tms.organization.domain.OrganizationUnit;
import com.acme.tms.organization.domain.OrganizationUnitStatus;
import com.acme.tms.organization.dto.CreateOrganizationUnitRequest;
import com.acme.tms.organization.dto.OrganizationUnitResponse;
import com.acme.tms.organization.dto.OrganizationUnitTreeResponse;
import com.acme.tms.organization.dto.UpdateOrganizationUnitRequest;
import com.acme.tms.organization.repository.OrganizationUnitRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrganizationUnitService {

    private final OrganizationUnitRepository organizationUnitRepository;
    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;

    public OrganizationUnitService(
        OrganizationUnitRepository organizationUnitRepository,
        ScopeEvaluator scopeEvaluator,
        CurrentUser currentUser
    ) {
        this.organizationUnitRepository = organizationUnitRepository;
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;
    }

    /**
     * Creation is authorized against the <em>parent</em>: a child needs {@code organization:create}
     * on the parent subtree, while a new root (a whole new tenant) is a global-only act.
     */
    @Transactional
    public OrganizationUnitResponse createScoped(CreateOrganizationUnitRequest request) {
        ScopeTarget target = request.parentOrganizationUnitId() == null
            ? ScopeTarget.global()
            : ScopeTarget.organization(request.parentOrganizationUnitId());

        if (!scopeEvaluator.hasPermission(currentUser.requireUserId(), "organization:create", target)) {
            throw new ScopeAccessDeniedException(
                "SCOPE_FORBIDDEN",
                request.parentOrganizationUnitId() == null
                    ? "Creating a root organization unit requires a global grant."
                    : "The parent organization unit is outside your scope."
            );
        }

        return create(request);
    }

    @Transactional(readOnly = true)
    public List<OrganizationUnitResponse> listVisible() {
        Set<UUID> visibleIds = Set.copyOf(
            scopeEvaluator.visibleOrganizationUnitIds(currentUser.requireUserId(), "organization:read")
        );

        return organizationUnitRepository.findByDeletedAtIsNullOrderByCreatedAtAsc()
            .stream()
            .filter(unit -> visibleIds.contains(unit.getId()))
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public OrganizationUnitResponse create(CreateOrganizationUnitRequest request) {
        if (request.parentOrganizationUnitId() != null) {
            OrganizationUnit parent = findActiveUnit(request.parentOrganizationUnitId());
            if (parent.getStatus() == OrganizationUnitStatus.ARCHIVED) {
                throw new ConflictException("UNIT_ARCHIVED", "Cannot create a child under an archived organization unit.");
            }
        }

        String slug = request.slug() == null || request.slug().isBlank()
            ? uniqueSlug(request.name())
            : request.slug();

        if (organizationUnitRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new ConflictException("SLUG_TAKEN", "Organization unit slug is already in use.");
        }

        OrganizationUnit organizationUnit = new OrganizationUnit();
        organizationUnit.setParentOrganizationUnitId(request.parentOrganizationUnitId());
        organizationUnit.setName(request.name().trim());
        organizationUnit.setSlug(slug);
        organizationUnit.setType(request.type());
        organizationUnit.setStatus(OrganizationUnitStatus.ACTIVE);

        return toResponse(organizationUnitRepository.save(organizationUnit));
    }

    @Transactional(readOnly = true)
    public OrganizationUnitResponse get(UUID id) {
        return toResponse(findActiveUnit(id));
    }

    @Transactional(readOnly = true)
    public OrganizationUnitTreeResponse tree(UUID id) {
        OrganizationUnit root = findActiveUnit(id);
        return toTree(root);
    }

    @Transactional
    public OrganizationUnitResponse update(UUID id, UpdateOrganizationUnitRequest request) {
        OrganizationUnit organizationUnit = findActiveUnit(id);
        if (organizationUnit.getStatus() == OrganizationUnitStatus.ARCHIVED) {
            throw new ConflictException("UNIT_ARCHIVED", "Archived organization units are read-only.");
        }

        if (request.name() != null && !request.name().isBlank()) {
            organizationUnit.setName(request.name().trim());
        }
        if (request.status() != null) {
            organizationUnit.setStatus(request.status());
            if (request.status() == OrganizationUnitStatus.ARCHIVED) {
                organizationUnit.markDeleted();
            }
        }

        return toResponse(organizationUnit);
    }

    @Transactional
    public void archive(UUID id) {
        OrganizationUnit organizationUnit = findActiveUnit(id);
        if (organizationUnit.getStatus() == OrganizationUnitStatus.ARCHIVED) {
            return;
        }
        organizationUnit.setStatus(OrganizationUnitStatus.ARCHIVED);
        organizationUnit.markDeleted();
    }

    public OrganizationUnit findActiveUnit(UUID id) {
        return organizationUnitRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("ORGANIZATION_UNIT_NOT_FOUND", "Organization unit not found."));
    }

    private OrganizationUnitTreeResponse toTree(OrganizationUnit organizationUnit) {
        List<OrganizationUnitTreeResponse> children = organizationUnitRepository
            .findByParentOrganizationUnitIdAndDeletedAtIsNullOrderByNameAsc(organizationUnit.getId())
            .stream()
            .sorted(Comparator.comparing(OrganizationUnit::getName))
            .map(this::toTree)
            .toList();

        return new OrganizationUnitTreeResponse(
            organizationUnit.getId(),
            organizationUnit.getName(),
            organizationUnit.getSlug(),
            organizationUnit.getType(),
            organizationUnit.getStatus(),
            children
        );
    }

    private OrganizationUnitResponse toResponse(OrganizationUnit organizationUnit) {
        return new OrganizationUnitResponse(
            organizationUnit.getId(),
            organizationUnit.getParentOrganizationUnitId(),
            organizationUnit.getName(),
            organizationUnit.getSlug(),
            organizationUnit.getType(),
            organizationUnit.getStatus(),
            organizationUnit.getCreatedAt()
        );
    }

    private String uniqueSlug(String name) {
        String baseSlug = SlugUtil.from(name, "organization-unit");
        String slug = baseSlug;
        int suffix = 2;

        while (organizationUnitRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            slug = baseSlug + "-" + suffix;
            suffix++;
        }

        return slug;
    }
}
