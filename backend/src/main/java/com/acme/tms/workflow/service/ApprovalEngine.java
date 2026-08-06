package com.acme.tms.workflow.service;

import com.acme.tms.workflow.domain.ApprovalDecision;
import com.acme.tms.workflow.domain.ApprovalInstanceStatus;

import java.util.List;
import java.util.Optional;

/**
 * The approval state machine, as a pure function.
 *
 * <p>Deliberately knows nothing about persistence, Spring or HTTP: 10_DEVELOPMENT_ROADMAP names
 * state-machine bugs a top risk for this sprint and asks for the transitions to be modelled as a
 * pure function and tested exhaustively before anything is wired to them. Every rule about levels
 * and terminal states lives here and nowhere else.
 *
 * <p>Whether a chain has one level or three is data, not code — that is the promise in the frozen
 * brief, and it holds because nothing below branches on the number of steps.
 */
public final class ApprovalEngine {

    private ApprovalEngine() {
    }

    /** The part of a step this engine cares about. Role and naming are the caller's concern. */
    public record Step(int level, String roleCode, boolean approvalRequired) {
    }

    /**
     * @param status what the instance becomes
     * @param currentLevel the level now awaiting action; unchanged once terminal
     */
    public record Outcome(ApprovalInstanceStatus status, int currentLevel) {

        public boolean isTerminal() {
            return status != ApprovalInstanceStatus.IN_PROGRESS;
        }
    }

    /**
     * The level a new instance opens at: the first that actually needs a decision.
     *
     * <p>Empty when every step is notify-only, which means there is nothing to wait for and the
     * caller should approve immediately.
     */
    public static Optional<Integer> firstActionableLevel(List<Step> steps) {
        return steps.stream()
            .filter(Step::approvalRequired)
            .map(Step::level)
            .min(Integer::compareTo);
    }

    /**
     * Applies a decision taken at {@code currentLevel}.
     *
     * <p>A rejection ends the chain wherever it happens — there is no partial rejection and no
     * sending it back a level. An approval advances to the next level that requires one, and
     * approving the last such level completes the chain.
     */
    public static Outcome apply(List<Step> steps, int currentLevel, ApprovalDecision decision) {
        if (decision == ApprovalDecision.REJECT) {
            return new Outcome(ApprovalInstanceStatus.REJECTED, currentLevel);
        }

        return nextActionableLevelAfter(steps, currentLevel)
            .map(next -> new Outcome(ApprovalInstanceStatus.IN_PROGRESS, next))
            .orElseGet(() -> new Outcome(ApprovalInstanceStatus.APPROVED, currentLevel));
    }

    /** Notify-only steps are skipped rather than waited on, so they never stall a chain. */
    private static Optional<Integer> nextActionableLevelAfter(List<Step> steps, int level) {
        return steps.stream()
            .filter(Step::approvalRequired)
            .map(Step::level)
            .filter(candidate -> candidate > level)
            .min(Integer::compareTo);
    }

    /** The single implicit step used when a tenant has configured no chain (doc 07 section 7.2). */
    public static List<Step> implicitSingleStep(String roleCode) {
        return List.of(new Step(1, roleCode, true));
    }
}
