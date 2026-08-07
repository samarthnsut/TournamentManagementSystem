package com.acme.tms.result.strategy;

import java.util.List;
import java.util.UUID;

/**
 * Every evaluated result in a competition, plus the full entrant roster so a team that has not
 * played yet still gets a row rather than vanishing from the table.
 *
 * <p>Names are deliberately absent: a strategy ranks identities, and the service attaches display
 * names when it builds the response.
 */
public record CompetitionResults(List<UUID> participantIds, List<MatchResultView> matches) {

    public record MatchResultView(
        UUID matchId,
        ResultOutcome outcome,
        List<ParticipantResult> participants
    ) {
    }
}
