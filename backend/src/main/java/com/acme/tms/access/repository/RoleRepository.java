package com.acme.tms.access.repository;

import com.acme.tms.access.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCodeAndDeletedAtIsNull(String code);
}
