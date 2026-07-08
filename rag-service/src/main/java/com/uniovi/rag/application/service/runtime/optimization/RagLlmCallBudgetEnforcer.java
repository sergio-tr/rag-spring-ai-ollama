package com.uniovi.rag.application.service.runtime.optimization;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-turn hard cap on secondary and total LLM calls. Bound for the duration of one orchestrated chat/query turn.
 */
public final class RagLlmCallBudgetEnforcer {

    public enum Decision {
        ALLOW,
        SKIP_OPTIONAL_BUDGET_EXCEEDED,
        FORCE_PRIMARY
    }

    private static final Logger log = LoggerFactory.getLogger(RagLlmCallBudgetEnforcer.class);

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private static final Set<String> PRIMARY_OPERATIONS =
            Set.of("primary-answer", "function-calling", "final-answer");

    private RagLlmCallBudgetEnforcer() {}

    public static void bind(ExecutionContext ctx) {
        if (ctx == null) {
            CURRENT.remove();
            return;
        }
        RagLlmCallBudgetPolicy.PresetBudget budget = RagLlmCallBudgetPolicy.budgetFor(ctx);
        CURRENT.set(new State(ctx, budget));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Snapshot snapshot() {
        State state = CURRENT.get();
        return state != null ? state.snapshot() : Snapshot.empty();
    }

    /** @return true when the call may proceed */
    public static boolean tryAllowSecondary(String operation) {
        State state = CURRENT.get();
        if (state == null) {
            return true;
        }
        return state.tryAllow(operation, false);
    }

    /** @return true when the primary call may proceed */
    public static boolean tryAllowPrimary(String operation) {
        State state = CURRENT.get();
        if (state == null) {
            return true;
        }
        return state.tryAllow(operation, true);
    }

    public static void recordCompleted(String operation) {
        State state = CURRENT.get();
        if (state == null) {
            return;
        }
        state.recordCompleted(operation);
    }

    public record Snapshot(
            int maxSecondary,
            int maxTotal,
            int usedSecondary,
            int usedTotal,
            boolean budgetExceeded) {

        static Snapshot empty() {
            return new Snapshot(0, 0, 0, 0, false);
        }
    }

    private static final class State {
        private final ExecutionContext ctx;
        private final int maxSecondary;
        private final int maxTotal;
        private int usedSecondary;
        private int usedTotal;
        private boolean budgetExceeded;

        State(ExecutionContext ctx, RagLlmCallBudgetPolicy.PresetBudget budget) {
            this.ctx = ctx;
            this.maxSecondary = Math.max(0, budget.maxSecondaryCalls());
            this.maxTotal = Math.max(1, budget.maxTotalCalls());
        }

        boolean tryAllow(String operation, boolean primary) {
            String op = normalize(operation);
            boolean isPrimary = primary || PRIMARY_OPERATIONS.contains(op);
            if (isPrimary) {
                if (usedTotal >= maxTotal) {
                    logBudget(Decision.FORCE_PRIMARY, op, "primary_required_despite_total_cap");
                    budgetExceeded = true;
                    return true;
                }
                return true;
            }
            if (usedSecondary >= maxSecondary || usedTotal >= maxTotal) {
                logBudget(Decision.SKIP_OPTIONAL_BUDGET_EXCEEDED, op, "optional_secondary_cap");
                budgetExceeded = true;
                return false;
            }
            logBudget(Decision.ALLOW, op, "within_budget");
            return true;
        }

        void recordCompleted(String operation) {
            String op = normalize(operation);
            usedTotal++;
            if (!PRIMARY_OPERATIONS.contains(op)) {
                usedSecondary++;
            }
        }

        Snapshot snapshot() {
            return new Snapshot(maxSecondary, maxTotal, usedSecondary, usedTotal, budgetExceeded);
        }

        private void logBudget(Decision decision, String callName, String reason) {
            log.info(
                    "RAG_LLM_BUDGET traceId={} conversationId={} presetId={} callName={} maxSecondary={} maxTotal={} usedSecondary={} usedTotal={} decision={} reason={}",
                    traceId(),
                    conversationId(),
                    presetId(),
                    callName,
                    maxSecondary,
                    maxTotal,
                    usedSecondary,
                    usedTotal,
                    decision,
                    reason);
        }

        private String traceId() {
            return ctx.correlationId() != null ? ctx.correlationId() : "";
        }

        private String conversationId() {
            return ctx.conversationId() != null ? ctx.conversationId().toString() : "";
        }

        private String presetId() {
            if (ctx.resolved() == null
                    || ctx.resolved().provenance() == null
                    || ctx.resolved().provenance().presetId() == null) {
                return "";
            }
            return ctx.resolved().provenance().presetId().toString();
        }
    }

    private static String normalize(String operation) {
        return Objects.requireNonNullElse(operation, "").trim().toLowerCase(Locale.ROOT);
    }
}
