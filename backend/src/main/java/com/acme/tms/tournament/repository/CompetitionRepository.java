package com.acme.tms.tournament.repository;

import com.acme.tms.tournament.domain.Competition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetitionRepository extends JpaRepository<Competition, UUID> {

    Optional<Competition> findByIdAndDeletedAtIsNull(UUID id);

    List<Competition> findByTournamentIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID tournamentId);

    boolean existsByTournamentIdAndDeletedAtIsNull(UUID tournamentId);

    boolean existsBySportConfigurationIdAndDeletedAtIsNull(UUID sportConfigurationId);
}
