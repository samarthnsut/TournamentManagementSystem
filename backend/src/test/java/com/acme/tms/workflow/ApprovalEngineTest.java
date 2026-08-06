package com.acme.tms.workflow;

import com.acme.tms.workflow.domain.ApprovalDecision;
import com.acme.tms.workflow.domain.ApprovalInstanceStatus;
import com.acme.tms.workflow.service.ApprovalEngine;
import com.acme.tms.workflow.service.ApprovalEngine.Outcome;
import com.acme.tms.workflow.service.ApprovalEngine.Step;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The state machine is tested here, in isolation, before any of it is wired to persistence — the
 * order 10_DEVELOPMENT_ROADMAP asks for, because an approval bug that only shows up through the
 * API is far more expensive to find.
 */
class ApprovalEngineTest {

    private static final List<Step> SINGLE = List.of(new Step(1, "TOURNAMENT_ADMIN", true));

    /** SAI's chain from doc 07 section 3.1: district, then state, then national. */
    private static final List<Step> THREE_LEVEL = List.of(
        new Step(1, "ORG_OFFICIAL", true),
        new Step(2, "ORG_OFFICIAL", true),
        new Step(3, "TENANT_ADMIN", true)
    );

    /** Level 2 is notify-only, so nobody should ever be asked to act on it. */
    private static final List<Step> WITH_NOTIFY_ONLY = List.of(
        new Step(1, "ORG_OFFICIAL", true),
        new Step(2, "ORG_OFFICIAL", false),
        new Step(3, "TENANT_ADMIN", true)
    );

    @Test
    void aChainOpensAtItsFirstLevel() {
        assertThat(ApprovalEngine.firstActionableLevel(THREE_LEVEL)).contains(1);
        assertThat(ApprovalEngine.firstActionableLevel(SINGLE)).contains(1);
    }

    @Test
    void aChainThatStartsWithNotifyOnlyStepsOpensAtTheFirstRealOne() {
        List<Step> steps = List.of(
            new Step(1, "ORG_OFFICIAL", false),
            new Step(2, "TENANT_ADMIN", true)
        );

        assertThat(ApprovalEngine.firstActionableLevel(steps)).contains(2);
    }

    @Test
    void aChainWithNothingToApproveHasNoLevelToWaitAt() {
        List<Step> steps = List.of(new Step(1, "ORG_OFFICIAL", false));

        assertThat(ApprovalEngine.firstActionableLevel(steps)).isEmpty();
        assertThat(ApprovalEngine.firstActionableLevel(List.of())).isEmpty();
    }

    @Test
    void approvingTheOnlyLevelCompletesTheChain() {
        Outcome outcome = ApprovalEngine.apply(SINGLE, 1, ApprovalDecision.APPROVE);

        assertThat(outcome.status()).isEqualTo(ApprovalInstanceStatus.APPROVED);
        assertThat(outcome.isTerminal()).isTrue();
    }

    @Test
    void approvingAdvancesOneLevelAtATime() {
        Outcome afterFirst = ApprovalEngine.apply(THREE_LEVEL, 1, ApprovalDecision.APPROVE);
        assertThat(afterFirst.status()).isEqualTo(ApprovalInstanceStatus.IN_PROGRESS);
        assertThat(afterFirst.currentLevel()).isEqualTo(2);

        Outcome afterSecond = ApprovalEngine.apply(THREE_LEVEL, 2, ApprovalDecision.APPROVE);
        assertThat(afterSecond.status()).isEqualTo(ApprovalInstanceStatus.IN_PROGRESS);
        assertThat(afterSecond.currentLevel()).isEqualTo(3);

        Outcome afterFinal = ApprovalEngine.apply(THREE_LEVEL, 3, ApprovalDecision.APPROVE);
        assertThat(afterFinal.status()).isEqualTo(ApprovalInstanceStatus.APPROVED);
    }

    @Test
    void approvingSkipsPastNotifyOnlyLevels() {
        // Level 2 never becomes the current level; approving level 1 jumps straight to 3.
        Outcome outcome = ApprovalEngine.apply(WITH_NOTIFY_ONLY, 1, ApprovalDecision.APPROVE);

        assertThat(outcome.currentLevel()).isEqualTo(3);
        assertThat(outcome.status()).isEqualTo(ApprovalInstanceStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void rejectingEndsTheChainWhereverItHappens(int level) {
        Outcome outcome = ApprovalEngine.apply(THREE_LEVEL, level, ApprovalDecision.REJECT);

        assertThat(outcome.status()).isEqualTo(ApprovalInstanceStatus.REJECTED);
        assertThat(outcome.currentLevel())
            .as("the level that rejected is preserved for the record")
            .isEqualTo(level);
    }

    @Test
    void oneLevelAndThreeLevelChainsRunThroughTheSameCode() {
        // The frozen brief promises 1 vs 3 levels is pure data. If this ever needs a branch on
        // step count, that promise has been broken.
        assertThat(ApprovalEngine.apply(SINGLE, 1, ApprovalDecision.APPROVE).status())
            .isEqualTo(ApprovalInstanceStatus.APPROVED);

        int level = ApprovalEngine.firstActionableLevel(THREE_LEVEL).orElseThrow();
        ApprovalInstanceStatus status = ApprovalInstanceStatus.IN_PROGRESS;
        int guard = 0;
        while (status == ApprovalInstanceStatus.IN_PROGRESS && guard++ < 10) {
            Outcome outcome = ApprovalEngine.apply(THREE_LEVEL, level, ApprovalDecision.APPROVE);
            status = outcome.status();
            level = outcome.currentLevel();
        }

        assertThat(status).isEqualTo(ApprovalInstanceStatus.APPROVED);
        assertThat(guard).as("three approvals, no more").isEqualTo(3);
    }

    @Test
    void aChainOfAnyLengthTakesExactlyThatManyApprovals() {
        for (int length = 1; length <= 6; length++) {
            List<Step> steps = new java.util.ArrayList<>();
            for (int level = 1; level <= length; level++) {
                steps.add(new Step(level, "ORG_OFFICIAL", true));
            }

            int current = ApprovalEngine.firstActionableLevel(steps).orElseThrow();
            int approvals = 0;
            ApprovalInstanceStatus status = ApprovalInstanceStatus.IN_PROGRESS;
            while (status == ApprovalInstanceStatus.IN_PROGRESS) {
                Outcome outcome = ApprovalEngine.apply(steps, current, ApprovalDecision.APPROVE);
                status = outcome.status();
                current = outcome.currentLevel();
                approvals++;
            }

            assertThat(approvals).as("chain of %d levels", length).isEqualTo(length);
            assertThat(status).isEqualTo(ApprovalInstanceStatus.APPROVED);
        }
    }

    @Test
    void levelsNeedNotBeContiguousForTheChainToWork() {
        // Gaps can appear if an intermediate step is removed from a draft chain.
        List<Step> steps = List.of(
            new Step(1, "ORG_OFFICIAL", true),
            new Step(5, "TENANT_ADMIN", true)
        );

        assertThat(ApprovalEngine.apply(steps, 1, ApprovalDecision.APPROVE).currentLevel()).isEqualTo(5);
        assertThat(ApprovalEngine.apply(steps, 5, ApprovalDecision.APPROVE).status())
            .isEqualTo(ApprovalInstanceStatus.APPROVED);
    }

    @Test
    void theImplicitChainIsASingleRequiredStep() {
        List<Step> implicit = ApprovalEngine.implicitSingleStep("TOURNAMENT_ADMIN");

        assertThat(implicit).hasSize(1);
        assertThat(implicit.get(0).approvalRequired()).isTrue();
        assertThat(ApprovalEngine.firstActionableLevel(implicit)).contains(1);
        assertThat(ApprovalEngine.apply(implicit, 1, ApprovalDecision.APPROVE).status())
            .isEqualTo(ApprovalInstanceStatus.APPROVED);
    }

    @Test
    void anEmptyChainHasNothingToWaitFor() {
        assertThat(ApprovalEngine.firstActionableLevel(List.of())).isEqualTo(Optional.empty());
    }
}
