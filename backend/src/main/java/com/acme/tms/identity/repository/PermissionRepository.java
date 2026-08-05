package com.acme.tms.identity.repository;

import com.acme.tms.identity.domain.Permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCodeAndDeletedAtIsNull(String code);
}
