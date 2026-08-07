package com.acme.tms.result.strategy;

import java.util.List;
import java.util.UUID;

/**
 * The pure view of a match an evaluator is given. Doc 06 section 4 sketches this as the Match
 * entity itself; passing a record instead keeps evaluators free of JPA and of anything they could
 * lazily navigate into a different sport's data.
 *
 * @param participantIds in slot order, so an evaluator that cares about home/away or lane order
 *     does not need the slot labels
 */
public record MatchContext(UUID matchId, List<UUID> participantIds) {
}
