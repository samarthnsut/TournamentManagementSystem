package com.acme.tms.result.repository;

import com.acme.tms.result.domain.LeaderboardEntry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeaderboardEntryRepository extends JpaRepository<LeaderboardEntry, UUID> {

    List<LeaderboardEntry> findByCompetitionIdOrderByRankAsc(UUID competitionId);

    void deleteByCompetitionId(UUID competitionId);
}
