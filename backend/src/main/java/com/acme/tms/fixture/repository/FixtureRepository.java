package com.acme.tms.fixture.repository;

import com.acme.tms.fixture.domain.Fixture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FixtureRepository extends JpaRepository<Fixture, UUID> {

    List<Fixture> findByCompetitionIdOrderByRoundNumberAsc(UUID competitionId);

    boolean existsByCompetitionId(UUID competitionId);

    void deleteByCompetitionId(UUID competitionId);
}
