package com.acme.tms.tournament.repository;

import com.acme.tms.tournament.domain.Sport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SportRepository extends JpaRepository<Sport, UUID> {

    Optional<Sport> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Sport> findByCodeAndDeletedAtIsNull(String code);

    List<Sport> findByDeletedAtIsNullOrderByNameAsc();
}
