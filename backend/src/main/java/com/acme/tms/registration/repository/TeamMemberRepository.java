package com.acme.tms.registration.repository;

import com.acme.tms.registration.domain.TeamMember;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    List<TeamMember> findByParticipantIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID participantId);
}
