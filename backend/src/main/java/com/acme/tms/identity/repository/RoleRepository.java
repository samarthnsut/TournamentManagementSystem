package com.acme.tms.identity.repository;

import com.acme.tms.identity.domain.Role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCodeAndDeletedAtIsNull(String code);
}
