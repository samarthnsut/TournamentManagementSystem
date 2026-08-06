package com.acme.tms.registration.repository;

import com.acme.tms.registration.domain.Participant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    Optional<Participant> findByIdAndDeletedAtIsNull(UUID id);
}
