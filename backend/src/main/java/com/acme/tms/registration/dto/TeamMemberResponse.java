package com.acme.tms.registration.dto;

import com.acme.tms.registration.domain.MemberRole;

import java.time.LocalDate;
import java.util.UUID;

public record TeamMemberResponse(
    UUID id,
    String fullName,
    LocalDate dateOfBirth,
    MemberRole memberRole,
    Integer jerseyNumber
) {
}
