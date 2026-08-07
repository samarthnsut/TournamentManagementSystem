package com.acme.tms.fixture.dto;

import java.util.UUID;

public record MatchParticipantResponse(UUID participantId, String name, String slot, Integer seed) {
}
