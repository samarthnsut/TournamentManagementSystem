package com.acme.tms.tournament.dto;

import java.time.Instant;
import java.util.UUID;

/** Shared shape for every lifecycle transition, per 08_API_CONTRACTS section 6.2. */
public record TransitionResponse(UUID id, String status, Instant transitionedAt) {
}
