package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryDecision;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryExecutionResult;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemorySlice;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical runtime-owned memory stage execution (P12).
 */
@Service
public class ConversationMemoryStrategy {

    private final ConversationMemoryPolicyResolver policyResolver;
    private final ConversationHistoryLoader historyLoader;
    private final ConversationMemorySelector selector;
    private final ConversationQuestionCondensor condensor;

    public ConversationMemoryStrategy(
            ConversationMemoryPolicyResolver policyResolver,
            ConversationHistoryLoader historyLoader,
            ConversationMemorySelector selector,
            ConversationQuestionCondensor condensor) {
        this.policyResolver = policyResolver;
        this.historyLoader = historyLoader;
        this.selector = selector;
        this.condensor = condensor;
    }

    public ConversationMemoryExecutionResult execute(
            ExecutionContext ctx,
            String preMemoryPlanningInputText) {
        return runMemory(ctx, preMemoryPlanningInputText, historyLoader.loadEligibleHistory(ctx));
    }

    /**
     * P18 replay: uses a precomputed eligible history window (messages with {@code seq} strictly before the
     * replayed user turn) instead of loading the live conversation tail.
     */
    public ConversationMemoryExecutionResult executeWithEligibleHistory(
            ExecutionContext ctx, String preMemoryPlanningInputText, List<ConversationMemoryTurn> eligibleHistory) {
        Objects.requireNonNull(eligibleHistory, "eligibleHistory");
        return runMemory(ctx, preMemoryPlanningInputText, List.copyOf(eligibleHistory));
    }

    private ConversationMemoryExecutionResult runMemory(
            ExecutionContext ctx,
            String preMemoryPlanningInputText,
            List<ConversationMemoryTurn> eligible) {
        ConversationMemoryDecision decision = policyResolver.resolve(ctx);
        if (!decision.attemptMemory()) {
            ConversationMemoryOutcome out =
                    !ctx.resolved().toRagConfig().memoryEnabled()
                            ? ConversationMemoryOutcome.DISABLED_BY_CONFIG
                            : ConversationMemoryOutcome.NO_CONVERSATION_SCOPE;
            return new ConversationMemoryExecutionResult(
                    out,
                    Optional.empty(),
                    false,
                    false,
                    false,
                    preMemoryPlanningInputText,
                    List.of());
        }

        List<ExecutionStageTrace> stages = new ArrayList<>();

        // Stage 1: history load (always present when memory attempted)
        stages.add(new ExecutionStageTrace(
                "memory_history_load",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "eligible_count=" + eligible.size()));

        if (eligible.isEmpty()) {
            return new ConversationMemoryExecutionResult(
                    ConversationMemoryOutcome.NO_HISTORY_AVAILABLE,
                    Optional.empty(),
                    false,
                    false,
                    false,
                    preMemoryPlanningInputText,
                    stages);
        }

        ConversationMemorySlice slice = selector.selectSlice(eligible, decision);
        stages.add(new ExecutionStageTrace(
                "memory_select_slice",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "selected_count=" + slice.totalTurnCount() + " selected_chars=" + slice.totalCharCount()));

        if (eligible.size() < 2) {
            stages.add(new ExecutionStageTrace(
                    "memory_finalize_planning_input",
                    0L,
                    ExecutionStageOutcome.SUCCESS,
                    "final_source=pre_memory_input"));
            return new ConversationMemoryExecutionResult(
                    ConversationMemoryOutcome.SINGLE_TURN_NO_MEMORY_NEEDED,
                    Optional.of(slice),
                    false,
                    false,
                    false,
                    preMemoryPlanningInputText,
                    stages);
        }

        Optional<String> deterministicExpand =
                ConversationFollowUpResolver.expand(eligible, ctx.userQuery());
        if (deterministicExpand.isPresent() && !deterministicExpand.get().isBlank()) {
            stages.add(
                    new ExecutionStageTrace(
                            "memory_follow_up_expand",
                            0L,
                            ExecutionStageOutcome.SUCCESS,
                            "status=DETERMINISTIC"));
            stages.add(
                    new ExecutionStageTrace(
                            "memory_finalize_planning_input",
                            0L,
                            ExecutionStageOutcome.SUCCESS,
                            "final_source=deterministic_follow_up"));
            return new ConversationMemoryExecutionResult(
                    ConversationMemoryOutcome.MEMORY_APPLIED,
                    Optional.of(slice),
                    false,
                    false,
                    false,
                    deterministicExpand.get(),
                    stages);
        }

        boolean condensationAttempted = decision.attemptCondensation();
        String condensed = "";
        boolean used = false;
        boolean fallbackApplied = false;
        ExecutionStageOutcome condenseOutcome = ExecutionStageOutcome.SUCCESS;
        String condenseMsg = "status=OK";
        try {
            condensed =
                    condensationAttempted
                            ? condensor.condense(ctx, slice, ctx.userQuery(), preMemoryPlanningInputText)
                            : "";
            if (condensed == null || condensed.isBlank()) {
                condenseOutcome = ExecutionStageOutcome.FAILED;
                condenseMsg = "status=BLANK_OUTPUT";
            } else {
                used = true;
            }
        } catch (Exception e) {
            condenseOutcome = ExecutionStageOutcome.FAILED;
            condenseMsg = "status=EXCEPTION message=" + safeMsg(e);
        }
        stages.add(new ExecutionStageTrace(
                "memory_condense",
                0L,
                condenseOutcome,
                condenseMsg));

        final String finalPlanningInput;
        final ConversationMemoryOutcome terminal;
        if (used) {
            finalPlanningInput = condensed;
            terminal = ConversationMemoryOutcome.MEMORY_APPLIED;
        } else {
            fallbackApplied = true;
            finalPlanningInput = preMemoryPlanningInputText;
            terminal = ConversationMemoryOutcome.CONDENSE_FAILED_FALLBACK;
        }

        String finalSource = used ? "final_source=condensed" : "final_source=fallback_pre_memory";
        stages.add(new ExecutionStageTrace(
                "memory_finalize_planning_input",
                0L,
                ExecutionStageOutcome.SUCCESS,
                finalSource));

        return new ConversationMemoryExecutionResult(
                terminal,
                Optional.of(slice),
                condensationAttempted,
                used,
                fallbackApplied,
                finalPlanningInput,
                stages);
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            return e.getClass().getSimpleName();
        }
        String t = m.trim();
        return t.isBlank() ? e.getClass().getSimpleName() : t;
    }
}
