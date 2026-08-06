package com.acme.tms.registration.dto;

import com.acme.tms.common.domain.ParticipantType;

import java.util.List;
import java.util.UUID;

public record ParticipantResponse(
    UUID id,
    ParticipantType participantType,
    String displayName,
    String contactEmail,
    List<TeamMemberResponse> members
) {
}
