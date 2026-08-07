package com.acme.tms.fixture.repository;

import com.acme.tms.fixture.domain.MatchParticipant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, UUID> {

    List<MatchParticipant> findByMatchIdOrderByCreatedAtAsc(UUID matchId);

    /** One query for a whole round, so rendering a fixture list is not N+1 in the number of matches. */
    List<MatchParticipant> findByMatchIdInOrderByCreatedAtAsc(Collection<UUID> matchIds);
}
