package com.acme.tms.result.strategy;

import com.acme.tms.common.domain.SportRules;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fastest recorded time first, for measured events. An entrant's best time across every heat they
 * ran is what counts, so adding rounds to an event needs no change here.
 *
 * <p>Entrants who produced no time at all are kept on the board, below everyone who did — a
 * disqualification should be visible, not silently erased.
 */
@Component
public class LowestTimeLeaderboard implements LeaderboardStrategy {

    @Override
    public LeaderboardStrategyKey key() {
        return LeaderboardStrategyKey.LOWEST_TIME;
    }

    @Override
    public Set<String> compatibleFixtureGenerators() {
        return Set.of("NONE", "ROUND_ROBIN");
    }

    @Override
    public List<LeaderboardRow> rank(CompetitionResults results, SportRules rules) {
        String defaultUnit = rules.getString("timeUnit", "SECONDS");

        Map<UUID, Best> bests = new LinkedHashMap<>();
        results.participantIds().forEach(participantId ->
            bests.put(participantId, new Best(participantId, defaultUnit)));

        for (CompetitionResults.MatchResultView match : results.matches()) {
            for (ParticipantResult participant : match.participants()) {
                bests.computeIfAbsent(
                    participant.participantId(),
                    participantId -> new Best(participantId, defaultUnit)
                ).record(participant);
            }
        }

        // No time ranks below every time, however slow.
        Comparator<Best> ranking = Comparator.comparing(
            (Best best) -> best.bestValue,
            Comparator.nullsLast(Comparator.naturalOrder())
        );

        List<Best> ordered = new ArrayList<>(bests.values());
        ordered.sort(ranking.thenComparing(best -> best.participantId));

        List<LeaderboardRow> rows = new ArrayList<>(ordered.size());
        int rank = 0;
        for (int index = 0; index < ordered.size(); index++) {
            Best best = ordered.get(index);
            boolean deadHeat = index > 0 && ranking.compare(ordered.get(index - 1), best) == 0;
            if (!deadHeat) {
                rank = index + 1;
            }
            rows.add(new LeaderboardRow(best.participantId, rank, best.metrics()));
        }

        return List.copyOf(rows);
    }

    private static final class Best {

        private final UUID participantId;
        private String unit;
        private BigDecimal bestValue;
        private int attempts;

        private Best(UUID participantId, String unit) {
            this.participantId = participantId;
            this.unit = unit;
        }

        private void record(ParticipantResult participant) {
            attempts++;
            if (participant.unit() != null) {
                unit = participant.unit();
            }
            if (participant.value() == null) {
                return;
            }
            if (bestValue == null || participant.value().compareTo(bestValue) < 0) {
                bestValue = participant.value();
            }
        }

        private Map<String, Object> metrics() {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("bestValue", bestValue);
            metrics.put("unit", unit);
            metrics.put("attempts", attempts);
            return metrics;
        }
    }
}
