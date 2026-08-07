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
 * League table ordered on points, then on the tiebreakers the tenant configured.
 *
 * <p>The metric names here are sport-neutral ({@code scoreFor}, not {@code goalsFor}) because the
 * same class ranks a football league and a chess Swiss. Tenants may still write their own
 * vocabulary in {@code rules.tiebreakers} — {@code GOAL_DIFFERENCE} and {@code SCORE_DIFFERENCE}
 * resolve to the same comparator — so a football organizer configures the table in football's
 * words without the engine learning any.
 */
@Component
public class PointsTableLeaderboard implements LeaderboardStrategy {

    /** What most leagues use, applied when a tenant configures no tiebreakers of their own. */
    private static final List<String> DEFAULT_TIEBREAKERS =
        List.of("SCORE_DIFFERENCE", "SCORE_FOR", "HEAD_TO_HEAD");

    @Override
    public LeaderboardStrategyKey key() {
        return LeaderboardStrategyKey.POINTS_TABLE;
    }

    @Override
    public Set<String> compatibleFixtureGenerators() {
        return Set.of("ROUND_ROBIN", "SWISS");
    }

    @Override
    public List<LeaderboardRow> rank(CompetitionResults results, SportRules rules) {
        Map<UUID, Tally> tallies = new LinkedHashMap<>();
        // Seeded from the roster first so an entrant who has not played yet still gets a row.
        results.participantIds().forEach(participantId -> tallies.put(participantId, new Tally(participantId)));

        for (CompetitionResults.MatchResultView match : results.matches()) {
            BigDecimal matchTotal = BigDecimal.ZERO;
            for (ParticipantResult participant : match.participants()) {
                if (participant.value() != null) {
                    matchTotal = matchTotal.add(participant.value());
                }
            }

            for (ParticipantResult participant : match.participants()) {
                Tally tally = tallies.computeIfAbsent(participant.participantId(), Tally::new);
                tally.record(participant, matchTotal);
            }
        }

        Comparator<Tally> ranking = Comparator.comparing((Tally tally) -> tally.points).reversed();
        for (String tiebreaker : rules.getStringList("tiebreakers", DEFAULT_TIEBREAKERS)) {
            ranking = ranking.thenComparing(comparatorFor(tiebreaker, results));
        }

        List<Tally> ordered = new ArrayList<>(tallies.values());
        // The id break is presentation only: it never changes a rank, it only stops two genuinely
        // tied entrants from swapping places between two reads of the same table.
        ordered.sort(ranking.thenComparing(tally -> tally.participantId));

        List<LeaderboardRow> rows = new ArrayList<>(ordered.size());
        int rank = 0;
        for (int index = 0; index < ordered.size(); index++) {
            Tally tally = ordered.get(index);
            boolean tiedWithPrevious = index > 0 && ranking.compare(ordered.get(index - 1), tally) == 0;
            if (!tiedWithPrevious) {
                rank = index + 1;
            }
            rows.add(new LeaderboardRow(tally.participantId, rank, tally.metrics()));
        }

        return List.copyOf(rows);
    }

    private Comparator<Tally> comparatorFor(String tiebreaker, CompetitionResults results) {
        return switch (tiebreaker.toUpperCase()) {
            case "SCORE_DIFFERENCE", "GOAL_DIFFERENCE", "POINT_DIFFERENCE" ->
                Comparator.comparing((Tally tally) -> tally.difference()).reversed();
            case "SCORE_FOR", "GOALS_FOR", "POINTS_FOR" ->
                Comparator.comparing((Tally tally) -> tally.scoreFor).reversed();
            case "WINS", "MOST_WINS" ->
                Comparator.comparingInt((Tally tally) -> tally.won).reversed();
            case "HEAD_TO_HEAD", "DIRECT_ENCOUNTER" -> headToHead(results);
            // An unrecognized name ranks nobody rather than failing: a typo in a tenant's config
            // must not take down a live standings page mid-tournament.
            default -> (first, second) -> 0;
        };
    }

    /**
     * Points each entrant took from the other in the matches they actually contested. Only
     * meaningful pairwise, which is all a comparator is ever asked about.
     */
    private Comparator<Tally> headToHead(CompetitionResults results) {
        return (first, second) -> {
            BigDecimal firstPoints = BigDecimal.ZERO;
            BigDecimal secondPoints = BigDecimal.ZERO;

            for (CompetitionResults.MatchResultView match : results.matches()) {
                ParticipantResult firstResult = null;
                ParticipantResult secondResult = null;
                for (ParticipantResult participant : match.participants()) {
                    if (participant.participantId().equals(first.participantId)) {
                        firstResult = participant;
                    } else if (participant.participantId().equals(second.participantId)) {
                        secondResult = participant;
                    }
                }
                if (firstResult != null && secondResult != null) {
                    firstPoints = firstPoints.add(firstResult.points());
                    secondPoints = secondPoints.add(secondResult.points());
                }
            }

            return secondPoints.compareTo(firstPoints);
        };
    }

    private static final class Tally {

        private final UUID participantId;
        private int played;
        private int won;
        private int drawn;
        private int lost;
        private BigDecimal points = BigDecimal.ZERO;
        private BigDecimal scoreFor = BigDecimal.ZERO;
        private BigDecimal scoreAgainst = BigDecimal.ZERO;

        private Tally(UUID participantId) {
            this.participantId = participantId;
        }

        private void record(ParticipantResult participant, BigDecimal matchTotal) {
            played++;
            points = points.add(participant.points());

            switch (participant.standing()) {
                case WIN -> won++;
                case DRAW -> drawn++;
                case LOSS -> lost++;
                default -> {
                    // RANKED and NO_CONTEST carry no win/draw/loss meaning in a table.
                }
            }

            if (participant.value() != null) {
                scoreFor = scoreFor.add(participant.value());
                // Whatever the rest of the match scored is what was scored against this entrant.
                scoreAgainst = scoreAgainst.add(matchTotal.subtract(participant.value()));
            }
        }

        private BigDecimal difference() {
            return scoreFor.subtract(scoreAgainst);
        }

        private Map<String, Object> metrics() {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("played", played);
            metrics.put("won", won);
            metrics.put("drawn", drawn);
            metrics.put("lost", lost);
            metrics.put("points", tidy(points));
            metrics.put("scoreFor", tidy(scoreFor));
            metrics.put("scoreAgainst", tidy(scoreAgainst));
            metrics.put("scoreDifference", tidy(difference()));
            return metrics;
        }

        /** Keeps 13 from serializing as 13.0 once a half-point sport has widened the scale. */
        private static BigDecimal tidy(BigDecimal value) {
            BigDecimal stripped = value.stripTrailingZeros();
            return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
        }
    }
}
