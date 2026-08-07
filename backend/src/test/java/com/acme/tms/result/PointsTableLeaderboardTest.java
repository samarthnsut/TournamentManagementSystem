package com.acme.tms.result;

import com.acme.tms.common.domain.SportRules;
import com.acme.tms.result.strategy.CompetitionResults;
import com.acme.tms.result.strategy.LeaderboardRow;
import com.acme.tms.result.strategy.ParticipantResult;
import com.acme.tms.result.strategy.PointsTableLeaderboard;
import com.acme.tms.result.strategy.ResultOutcome;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tie-breaking is the part of a league table people argue about, so the cases here are worked
 * examples: each one fixes the standings up to the tie and varies only what breaks it.
 */
class PointsTableLeaderboardTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final UUID D = UUID.randomUUID();

    private final PointsTableLeaderboard leaderboard = new PointsTableLeaderboard();

    private SportRules rules(String... tiebreakers) {
        ObjectNode node = new ObjectMapper().createObjectNode();
        if (tiebreakers.length > 0) {
            ArrayNode array = node.putArray("tiebreakers");
            for (String tiebreaker : tiebreakers) {
                array.add(tiebreaker);
            }
        }
        return SportRules.of(node);
    }

    /** A played result at the standard three-one-nil. */
    private CompetitionResults.MatchResultView played(UUID home, int homeScore, UUID away, int awayScore) {
        return new CompetitionResults.MatchResultView(
            UUID.randomUUID(),
            ResultOutcome.COMPLETED,
            List.of(
                side(home, homeScore, awayScore),
                side(away, awayScore, homeScore)
            )
        );
    }

    private ParticipantResult side(UUID participantId, int scored, int conceded) {
        ParticipantResult.Standing standing = scored > conceded
            ? ParticipantResult.Standing.WIN
            : scored < conceded ? ParticipantResult.Standing.LOSS : ParticipantResult.Standing.DRAW;
        BigDecimal points = switch (standing) {
            case WIN -> BigDecimal.valueOf(3);
            case LOSS -> BigDecimal.ZERO;
            default -> BigDecimal.ONE;
        };
        return new ParticipantResult(participantId, BigDecimal.valueOf(scored), null, points, standing);
    }

    private List<LeaderboardRow> rank(List<UUID> roster, List<CompetitionResults.MatchResultView> matches, SportRules rules) {
        return leaderboard.rank(new CompetitionResults(roster, matches), rules);
    }

    private List<UUID> orderOf(List<LeaderboardRow> rows) {
        List<UUID> order = new ArrayList<>(rows.size());
        rows.forEach(row -> order.add(row.participantId()));
        return order;
    }

    private LeaderboardRow rowFor(List<LeaderboardRow> rows, UUID participantId) {
        return rows.stream()
            .filter(row -> row.participantId().equals(participantId))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void morePointsRanksHigher() {
        List<LeaderboardRow> rows = rank(
            List.of(A, B, C),
            List.of(played(A, 2, B, 0), played(B, 1, C, 0), played(A, 1, C, 1)),
            rules()
        );

        assertThat(orderOf(rows)).containsExactly(A, B, C);
        assertThat(rowFor(rows, A).rank()).isEqualTo(1);
    }

    @Test
    void theTallyAddsUpAgainstTheMatchesItSummarizes() {
        List<LeaderboardRow> rows = rank(
            List.of(A, B),
            List.of(played(A, 3, B, 1), played(A, 1, B, 1)),
            rules()
        );

        LeaderboardRow a = rowFor(rows, A);
        assertThat(a.metrics()).containsEntry("played", 2);
        assertThat(a.metrics()).containsEntry("won", 1);
        assertThat(a.metrics()).containsEntry("drawn", 1);
        assertThat(a.metrics()).containsEntry("lost", 0);
        assertThat(a.metrics().get("points")).isEqualTo(BigDecimal.valueOf(4));
        assertThat(a.metrics().get("scoreFor")).isEqualTo(BigDecimal.valueOf(4));
        assertThat(a.metrics().get("scoreAgainst")).isEqualTo(BigDecimal.valueOf(2));
        assertThat(a.metrics().get("scoreDifference")).isEqualTo(BigDecimal.valueOf(2));
    }

    @Test
    void levelOnPointsIsBrokenByScoreDifference() {
        // A and B both win one and lose one; A's win was by more.
        List<LeaderboardRow> rows = rank(
            List.of(A, B, C, D),
            List.of(
                played(A, 5, C, 0),
                played(D, 1, A, 0),
                played(B, 2, D, 1),
                played(C, 1, B, 0)
            ),
            rules("SCORE_DIFFERENCE", "SCORE_FOR", "HEAD_TO_HEAD")
        );

        assertThat(rowFor(rows, A).metrics().get("points")).isEqualTo(BigDecimal.valueOf(3));
        assertThat(rowFor(rows, B).metrics().get("points")).isEqualTo(BigDecimal.valueOf(3));
        assertThat(orderOf(rows).indexOf(A)).isLessThan(orderOf(rows).indexOf(B));
    }

    @Test
    void footballsVocabularyResolvesToTheSameComparator() {
        // A tenant writing GOAL_DIFFERENCE must get exactly what SCORE_DIFFERENCE gives, or the
        // engine would be quietly ignoring their configuration.
        List<CompetitionResults.MatchResultView> matches = List.of(
            played(A, 5, C, 0),
            played(D, 1, A, 0),
            played(B, 2, D, 1),
            played(C, 1, B, 0)
        );

        assertThat(orderOf(rank(List.of(A, B, C, D), matches, rules("GOAL_DIFFERENCE"))))
            .isEqualTo(orderOf(rank(List.of(A, B, C, D), matches, rules("SCORE_DIFFERENCE"))));
    }

    @Test
    void anEqualDifferenceIsBrokenByGoalsScored() {
        // Both won 1-0 and lost 2-1: same points, same difference, B scored more overall.
        List<LeaderboardRow> rows = rank(
            List.of(A, B, C, D),
            List.of(
                played(A, 1, C, 0),
                played(D, 2, A, 1),
                played(B, 3, D, 2),
                played(C, 3, B, 2)
            ),
            rules("SCORE_DIFFERENCE", "SCORE_FOR")
        );

        assertThat(rowFor(rows, A).metrics().get("scoreDifference"))
            .isEqualTo(rowFor(rows, B).metrics().get("scoreDifference"));
        assertThat(rowFor(rows, B).metrics().get("scoreFor")).isEqualTo(BigDecimal.valueOf(5));
        assertThat(rowFor(rows, A).metrics().get("scoreFor")).isEqualTo(BigDecimal.valueOf(2));
        assertThat(orderOf(rows).indexOf(B)).isLessThan(orderOf(rows).indexOf(A));
    }

    @Test
    void headToHeadSeparatesTeamsThatAreOtherwiseIdentical() {
        // A and B are level on points, difference and goals scored; A beat B when they met.
        List<LeaderboardRow> rows = rank(
            List.of(A, B),
            List.of(played(A, 1, B, 0), played(B, 1, A, 0), played(A, 2, B, 1), played(B, 2, A, 1)),
            rules("SCORE_DIFFERENCE", "SCORE_FOR", "HEAD_TO_HEAD")
        );

        assertThat(rowFor(rows, A).metrics().get("points")).isEqualTo(rowFor(rows, B).metrics().get("points"));
        // Four meetings, two wins each — genuinely inseparable, so they share the rank.
        assertThat(rows).extracting(LeaderboardRow::rank).containsExactly(1, 1);
    }

    @Test
    void headToHeadPutsTheTeamThatWonTheMeetingAhead() {
        List<LeaderboardRow> rows = rank(
            List.of(A, B),
            List.of(played(A, 1, B, 0), played(B, 1, A, 0), played(A, 1, B, 0)),
            rules("HEAD_TO_HEAD")
        );

        assertThat(orderOf(rows)).containsExactly(A, B);
    }

    @Test
    void trulyTiedEntrantsShareARankAndTheNextRankSkips() {
        List<LeaderboardRow> rows = rank(
            List.of(A, B, C),
            List.of(played(A, 1, C, 0), played(B, 1, C, 0)),
            rules()
        );

        // A and B each beat C by the same score and never met; C is alone at the bottom.
        assertThat(rows).extracting(LeaderboardRow::rank).containsExactly(1, 1, 3);
    }

    @Test
    void anEntrantWhoHasNotPlayedStillAppearsOnTheTable() {
        List<LeaderboardRow> rows = rank(
            List.of(A, B, C, D),
            List.of(played(A, 1, B, 0)),
            rules()
        );

        assertThat(rows).hasSize(4);
        LeaderboardRow d = rowFor(rows, D);
        assertThat(d.metrics()).containsEntry("played", 0);
        assertThat(d.metrics().get("points")).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void aWalkoverCountsAsAPlayedMatch() {
        CompetitionResults.MatchResultView walkover = new CompetitionResults.MatchResultView(
            UUID.randomUUID(),
            ResultOutcome.WALKOVER,
            List.of(
                new ParticipantResult(A, null, null, BigDecimal.valueOf(3), ParticipantResult.Standing.WIN),
                new ParticipantResult(B, null, null, BigDecimal.ZERO, ParticipantResult.Standing.LOSS)
            )
        );

        List<LeaderboardRow> rows = rank(List.of(A, B), List.of(walkover), rules());

        assertThat(rowFor(rows, A).metrics()).containsEntry("played", 1).containsEntry("won", 1);
        assertThat(rowFor(rows, A).metrics().get("points")).isEqualTo(BigDecimal.valueOf(3));
        // No goals were scored, so the difference must not be invented.
        assertThat(rowFor(rows, A).metrics().get("scoreFor")).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void halfPointScoringSerializesWithoutATrailingZero() {
        // Chess-readiness: a draw is worth half a point, and 1.5 must not become 1.50.
        CompetitionResults.MatchResultView drawn = new CompetitionResults.MatchResultView(
            UUID.randomUUID(),
            ResultOutcome.COMPLETED,
            List.of(
                new ParticipantResult(A, null, null, new BigDecimal("0.5"), ParticipantResult.Standing.DRAW),
                new ParticipantResult(B, null, null, new BigDecimal("0.5"), ParticipantResult.Standing.DRAW)
            )
        );
        CompetitionResults.MatchResultView won = new CompetitionResults.MatchResultView(
            UUID.randomUUID(),
            ResultOutcome.COMPLETED,
            List.of(
                new ParticipantResult(A, null, null, BigDecimal.ONE, ParticipantResult.Standing.WIN),
                new ParticipantResult(B, null, null, BigDecimal.ZERO, ParticipantResult.Standing.LOSS)
            )
        );

        List<LeaderboardRow> rows = rank(List.of(A, B), List.of(drawn, won), rules());

        assertThat(rowFor(rows, A).metrics().get("points").toString()).isEqualTo("1.5");
        assertThat(rowFor(rows, B).metrics().get("points").toString()).isEqualTo("0.5");
    }

    @Test
    void anUnrecognizedTiebreakerIsIgnoredRatherThanBreakingTheBoard() {
        // A typo in a tenant's config must not take a live standings page down mid-tournament.
        List<LeaderboardRow> rows = rank(
            List.of(A, B),
            List.of(played(A, 2, B, 0)),
            rules("BUCHHOLZ_THAT_NOBODY_IMPLEMENTED")
        );

        assertThat(orderOf(rows)).containsExactly(A, B);
    }

    @Test
    void rankingTheSameResultsTwiceGivesTheSameBoard() {
        // BR-LE-2: the board is recomputed from scratch on every result, so it has to be stable.
        List<CompetitionResults.MatchResultView> matches =
            List.of(played(A, 1, B, 1), played(C, 0, D, 0), played(A, 2, C, 2));

        assertThat(orderOf(rank(List.of(A, B, C, D), matches, rules())))
            .isEqualTo(orderOf(rank(List.of(A, B, C, D), matches, rules())));
    }
}
