package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.Decision;
import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.ParentBaseline;
import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.WinnerKind;
import java.util.Optional;

/** P7 deterministic-route policy: safe P6 campaign parent is the default floor unless native tool/retrieval is demonstrably stronger. */
public final class P7BaselineFloorSelector {

    public enum P7WinnerKind {
        PARENT_P6,
        TOOL,
        RETRIEVAL,
        ABSTENTION
    }

    public record P7Decision(
            P7WinnerKind winner,
            Optional<ParentBaseline> baseline,
            Optional<MonotonicRouteSafetyService.CandidateScore> nativeWinner,
            boolean baselineCandidateSelected,
            boolean baselineOverrideAttempted,
            boolean baselineOverrideAccepted,
            String baselineOverrideRejectedReason,
            boolean monotonicFloorApplied,
            boolean monotonicFloorPreventedRegression) {}

    private P7BaselineFloorSelector() {}

    public static P7Decision resolve(
            Optional<ParentBaseline> p6Baseline,
            Optional<MonotonicRouteSafetyService.CandidateScore> tool,
            Optional<MonotonicRouteSafetyService.CandidateScore> retrieval,
            boolean abstentionRequired) {
        Decision decision =
                P15BaselineFloorSelector.resolve(
                        Optional.empty(),
                        p6Baseline,
                        Optional.empty(),
                        tool,
                        retrieval,
                        abstentionRequired);
        return fromP15(decision);
    }

    static P7Decision fromP15(Decision decision) {
        P7WinnerKind winner =
                switch (decision.winner()) {
                    case PARENT_P6 -> P7WinnerKind.PARENT_P6;
                    case TOOL -> P7WinnerKind.TOOL;
                    case RETRIEVAL -> P7WinnerKind.RETRIEVAL;
                    case ABSTENTION -> P7WinnerKind.ABSTENTION;
                    default -> throw new IllegalStateException("unsupported P7 floor winner: " + decision.winner());
                };
        return new P7Decision(
                winner,
                decision.baseline(),
                decision.nativeWinner(),
                decision.baselineCandidateSelected(),
                decision.baselineOverrideAttempted(),
                decision.baselineOverrideAccepted(),
                decision.baselineOverrideRejectedReason(),
                decision.monotonicFloorApplied(),
                decision.monotonicFloorPreventedRegression());
    }

    public static Decision toP15Decision(P7Decision decision) {
        WinnerKind winner =
                switch (decision.winner()) {
                    case PARENT_P6 -> WinnerKind.PARENT_P6;
                    case TOOL -> WinnerKind.TOOL;
                    case RETRIEVAL -> WinnerKind.RETRIEVAL;
                    case ABSTENTION -> WinnerKind.ABSTENTION;
                };
        return new Decision(
                winner,
                decision.baseline(),
                decision.nativeWinner(),
                decision.baselineCandidateSelected(),
                decision.baselineOverrideAttempted(),
                decision.baselineOverrideAccepted(),
                decision.baselineOverrideRejectedReason(),
                decision.monotonicFloorApplied(),
                decision.monotonicFloorPreventedRegression());
    }
}
