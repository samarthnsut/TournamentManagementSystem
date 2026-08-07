package com.acme.tms.fixture.dto;

import java.util.List;

/**
 * @param warnings non-blocking problems with the slot that was booked. Venue double-booking is a
 *     warning rather than a rejection in V1 (BR-M-4): organizers legitimately run two events on one
 *     ground, and refusing the booking would have them scheduling around the software.
 */
public record ScheduledMatchResponse(MatchResponse match, List<String> warnings) {
}
