package com.acme.tms.registration.dto;

import com.acme.tms.common.domain.ParticipantType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** A participant created inline with a registration, for entrants who have no record yet. */
public record ParticipantRequest(
    @NotNull ParticipantType participantType,
    @NotBlank @Size(max = 200) String displayName,
    @Size(max = 320) String contactEmail,
    @Valid List<TeamMemberRequest> members
) {
}
