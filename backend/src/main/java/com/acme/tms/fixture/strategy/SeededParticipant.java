package com.acme.tms.fixture.strategy;

import java.util.UUID;

/**
 * One approved entrant offered to a generator.
 *
 * @param seed null when the organizer asked for a random draw; generators that care about seeding
 *     order sort on it and fall back to the list order when it is absent
 */
public record SeededParticipant(UUID participantId, String displayName, Integer seed) {

    public static SeededParticipant unseeded(UUID participantId, String displayName) {
        return new SeededParticipant(participantId, displayName, null);
    }
}
