package com.acme.tms.registration.repository;

import com.acme.tms.registration.domain.Registration;
import com.acme.tms.registration.domain.RegistrationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    Optional<Registration> findByIdAndDeletedAtIsNull(UUID id);

    List<Registration> findByCompetitionIdAndDeletedAtIsNullOrderBySubmittedAtAsc(UUID competitionId);

    /** The entrant list fixtures are drawn from and leaderboards are seeded with (BR-F-2). */
    List<Registration> findByCompetitionIdAndStatusAndDeletedAtIsNullOrderBySubmittedAtAsc(
        UUID competitionId, RegistrationStatus status);

    /** A withdrawn entry is not live, so it neither blocks re-entry nor fills a slot. */
    boolean existsByCompetitionIdAndParticipantIdAndStatusNotAndDeletedAtIsNull(
        UUID competitionId, UUID participantId, RegistrationStatus excludedStatus);

    long countByCompetitionIdAndStatusNotAndDeletedAtIsNull(UUID competitionId, RegistrationStatus excludedStatus);
}
