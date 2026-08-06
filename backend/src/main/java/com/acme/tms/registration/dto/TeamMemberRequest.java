package com.acme.tms.registration.dto;

import com.acme.tms.registration.domain.MemberRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TeamMemberRequest(
    @NotBlank @Size(max = 200) String fullName,
    LocalDate dateOfBirth,
    MemberRole memberRole,
    Integer jerseyNumber
) {
}
