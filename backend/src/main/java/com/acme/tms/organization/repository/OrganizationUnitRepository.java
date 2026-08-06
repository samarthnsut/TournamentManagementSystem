package com.acme.tms.organization.repository;

import com.acme.tms.organization.domain.OrganizationUnit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, UUID> {

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    Optional<OrganizationUnit> findByIdAndDeletedAtIsNull(UUID id);

    List<OrganizationUnit> findByDeletedAtIsNullOrderByCreatedAtAsc();

    List<OrganizationUnit> findByParentOrganizationUnitIdAndDeletedAtIsNullOrderByNameAsc(UUID parentOrganizationUnitId);

    /**
     * Ids of every unit at or beneath the given roots. An ORGANIZATION-scoped grant applies to the
     * whole subtree (BR-OU-3), so this is what turns a grant into the set of units it reaches.
     */
    @Query(value = """
        with recursive subtree as (
            select id from organization_unit
            where id in (:rootIds) and deleted_at is null
            union all
            select child.id from organization_unit child
            join subtree on child.parent_organization_unit_id = subtree.id
            where child.deleted_at is null
        )
        select id from subtree
        """, nativeQuery = true)
    List<UUID> findSubtreeIds(@Param("rootIds") Collection<UUID> rootIds);

    /**
     * The unit itself and every ancestor above it, nearest first.
     *
     * <p>Approval workflows resolve by walking upwards until one is found, so a state association
     * can override the federation's chain for its own subtree (doc 07 section 4.1). The ordering is
     * the whole point: the first hit wins.
     */
    @Query(value = """
        with recursive ancestry as (
            select id, parent_organization_unit_id, 0 as depth from organization_unit
            where id = :unitId and deleted_at is null
            union all
            select parent.id, parent.parent_organization_unit_id, ancestry.depth + 1
            from organization_unit parent
            join ancestry on ancestry.parent_organization_unit_id = parent.id
            where parent.deleted_at is null
        )
        select id from ancestry order by depth
        """, nativeQuery = true)
    List<UUID> findAncestorIdsNearestFirst(@Param("unitId") UUID unitId);
}

