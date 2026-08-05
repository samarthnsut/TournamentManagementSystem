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
}

