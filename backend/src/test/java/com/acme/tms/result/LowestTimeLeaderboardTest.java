package com.acme.tms.result;

import com.acme.tms.common.domain.SportRules;
import com.acme.tms.result.strategy.CompetitionResults;
import com.acme.tms.result.strategy.LeaderboardRow;
import com.acme.tms.result.strategy.LowestTimeLeaderboard;
import com.acme.tms.result.strategy.ParticipantResult;
import com.acme.tms.result.strategy.ResultOutcome;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LowestTimeLeaderboardTest {

    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static final UUID THIRD = UUID.randomUUID();

    private final LowestTimeLeaderboard leaderboard = new LowestTimeLeaderboard();

    private SportRules rules() {
        return SportRules.of(new ObjectMapper().createObjectNode().put("timeUnit", "SECONDS"));
    }

    private ParticipantResult timed(UUID participantId, String time) {
        return new ParticipantResult(
            participantId,
            time == null ? null : new BigDecimal(time),
            "SECONDS",
            BigDecimal.ZERO,
            time == null ? ParticipantResult.Standing.NO_CONTEST : ParticipantResult.Standing.RANKED
        );
    }

    private CompetitionResults.MatchResultView heat(ParticipantResult... results) {
        return new CompetitionResults.MatchResultView(
            UUID.randomUUID(), ResultOutcome.COMPLETED, List.of(results));
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
    void theFastestTimeRanksFirst() {
        List<LeaderboardRow> rows = leaderboard.rank(new CompetitionResults(
            List.of(FIRST, SECOND, THIRD),
            List.of(heat(timed(FIRST, "11.42"), timed(SECOND, "10.98"), timed(THIRD, "12.01")))
        ), rules());

        assertThat(orderOf(rows)).containsExactly(SECOND, FIRST, THIRD);
        assertThat(rows).extracting(LeaderboardRow::rank).containsExactly(1, 2, 3);
    }

    @Test
    void theBestTimeAcrossHeatsIsTheOneThatCounts() {
        List<LeaderboardRow> rows = leaderboard.rank(new CompetitionResults(
            List.of(FIRST, SECOND),
            List.of(
                heat(timed(FIRST, "11.42"), timed(SECOND, "10.98")),
                heat(timed(FIRST, "10.55"), timed(SECOND, "11.20"))
            )
        ), rules());

        assertThat(rowFor(rows, FIRST).metrics().get("bestValue")).isEqualTo(new BigDecimal("10.55"));
        assertThat(rowFor(rows, FIRST).metrics()).containsEntry("attempts", 2);
        assertThat(orderOf(rows)).containsExactly(FIRST, SECOND);
    }

    @Test
    void aRunnerWithNoTimeRanksBelowEveryoneWhoFinished() {
        List<LeaderboardRow> rows = leaderboard.rank(new CompetitionResults(
            List.of(FIRST, SECOND, THIRD),
            List.of(heat(timed(FIRST, null), timed(SECOND, "13.90"), timed(THIRD, "12.01")))
        ), rules());

        assertThat(orderOf(rows)).containsExactly(THIRD, SECOND, FIRST);
        assertThat(rowFor(rows, FIRST).metrics().get("bestValue")).isNull();
    }

    @Test
    void aDeadHeatSharesTheRank() {
        List<LeaderboardRow> rows = leaderboard.rank(new CompetitionResults(
            List.of(FIRST, SECOND, THIRD),
            List.of(heat(timed(FIRST, "11.42"), timed(SECOND, "11.42"), timed(THIRD, "12.01")))
        ), rules());

        assertThat(rows).extracting(LeaderboardRow::rank).containsExactly(1, 1, 3);
    }

    @Test
    void theUnitIsCarriedOntoTheBoard() {
        List<LeaderboardRow> rows = leaderboard.rank(new CompetitionResults(
            List.of(FIRST),
            List.of(heat(timed(FIRST, "11.42")))
        ), rules());

        assertThat(rowFor(rows, FIRST).metrics()).containsEntry("unit", "SECONDS");
    }

    @Test
    void anEntrantWhoHasNotRunStillAppears() {
        List<LeaderboardRow> rows = leaderboard.rank(new CompetitionResults(
            List.of(FIRST, SECOND, THIRD),
            List.of(heat(timed(FIRST, "11.42")))
        ), rules());

        assertThat(rows).hasSize(3);
        assertThat(rowFor(rows, THIRD).metrics()).containsEntry("attempts", 0);
    }
}
