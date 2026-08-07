package com.acme.tms.result.repository;

import com.acme.tms.result.domain.Result;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultRepository extends JpaRepository<Result, UUID> {

    Optional<Result> findByMatchId(UUID matchId);

    List<Result> findByMatchIdIn(Collection<UUID> matchIds);

    void deleteByMatchIdIn(Collection<UUID> matchIds);
}
