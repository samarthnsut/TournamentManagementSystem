package com.acme.tms.fixture.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record ScheduleMatchRequest(@NotNull Instant scheduledAt, UUID venueId) {
}
