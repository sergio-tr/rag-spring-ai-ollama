package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.application.service.runtime.query.ActaFieldAnchorHeuristics;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Blocks corpus retrieval for conversation-meta recall when no eligible history exists (P12).
 * Applies even when {@code memoryEnabled=false}: meta-recall must not fall through to corpus RAG.
 */
@Component
public class ConversationRecallGuard {

    private static final Pattern DATE_SLASH = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b");

    private final ConversationHistoryLoader historyLoader;

    public ConversationRecallGuard(ConversationHistoryLoader historyLoader) {
        this.historyLoader = Objects.requireNonNull(historyLoader, "historyLoader");
    }

    public boolean shouldShortCircuit(ExecutionContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        String query = ctx.userQuery();
        if (!isConversationMetaRecallQuery(query)) {
            return false;
        }
        if (isCompoundMetaAndCorpusQuery(query)) {
            return false;
        }
        if (hasEligibleConversationHistory(ctx)) {
            return false;
        }
        return true;
    }

    /**
     * Blocks corpus retrieval for acta-scoped follow-ups when this conversation has no local date/meeting anchor
     * (FD-ISO-01). Applies when clarification did not already ask — including presets with clarification disabled.
     */
    public boolean shouldShortCircuitAmbiguousActaQuery(ExecutionContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        String query = effectiveQueryForActaGuard(ctx);
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        if (ActaFieldAnchorHeuristics.isCompoundMonthTopicAttendeeFilter(q)) {
            return false;
        }
        if (isConversationMetaRecallQuery(query)) {
            return false;
        }
        if (!isAmbiguousActaScopedWithoutDate(query)) {
            return false;
        }
        if (hasLocalConversationActaAnchor(ctx)) {
            return false;
        }
        return true;
    }

    /**
     * True when the user asks what was discussed previously in this chat (not a corpus/acta question).
     */
    public static boolean isConversationMetaRecallQuery(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return false;
        }
        String q = userQuery.toLowerCase(Locale.ROOT);
        return q.contains("hablamos antes")
                || q.contains("de qué hablamos")
                || q.contains("de que hablamos")
                || q.contains("qué dijimos")
                || q.contains("que dijimos")
                || q.contains("en nuestra conversación")
                || q.contains("en nuestra conversacion")
                || q.contains("en esta conversación")
                || q.contains("en esta conversacion")
                || q.contains("conversación anterior")
                || q.contains("conversacion anterior");
    }

    /**
     * Meta-recall combined with a substantive acta/corpus question should continue through RAG/memory.
     */
    public static boolean isCompoundMetaAndCorpusQuery(String userQuery) {
        if (!isConversationMetaRecallQuery(userQuery)) {
            return false;
        }
        String q = userQuery.toLowerCase(Locale.ROOT);
        return q.contains("presidió")
                || q.contains("presidio")
                || q.contains("presidente")
                || q.contains("participantes")
                || q.contains("asistieron")
                || q.contains("asistentes")
                || q.contains("esa reunión")
                || q.contains("esa reunion")
                || q.contains("esa acta")
                || DATE_SLASH.matcher(q).find();
    }

    private boolean hasEligibleConversationHistory(ExecutionContext ctx) {
        ConversationMemoryOutcome outcome = ctx.memoryOutcome();
        if (outcome == ConversationMemoryOutcome.MEMORY_APPLIED
                || outcome == ConversationMemoryOutcome.CONDENSE_FAILED_FALLBACK) {
            return true;
        }
        List<ConversationMemoryTurn> history = historyLoader.loadEligibleHistory(ctx);
        return history.size() >= 2;
    }

    public static String noEligibleHistoryResponse() {
        return "No hemos hablado antes en esta conversación; esta es la primera pregunta.";
    }

    /** Frozen missing-date clarification text (same template as P11 {@link ClarificationQuestionKind#MISSING_DATE}). */
    public static String missingActaDateResponse() {
        return ClarificationQuestionKind.MISSING_DATE.templateText();
    }

    static boolean isAmbiguousActaScopedWithoutDate(String userQuery) {
        String q = userQuery.toLowerCase(Locale.ROOT);
        if (ActaFieldAnchorHeuristics.isCompoundMonthTopicAttendeeFilter(q)) {
            return false;
        }
        if (ActaFieldAnchorHeuristics.hasExplicitDateInText(q) || ActaFieldAnchorHeuristics.isCorpusWideAggregate(q)) {
            return false;
        }
        boolean participants =
                q.contains("participantes")
                        || q.contains("asistieron")
                        || q.contains("asistentes")
                        || q.contains("asistió")
                        || q.contains("asistio");
        boolean countParticipants =
                (q.contains("cuántos") || q.contains("cuantos") || q.contains("cuántas") || q.contains("cuantas"))
                        && participants;
        boolean pronounFollowUp =
                q.contains("ellos")
                        || q.contains("ellas")
                        || q.contains("quiénes fueron")
                        || q.contains("quienes fueron");
        boolean demonstrativeFollowUp =
                q.contains("esa reunión")
                        || q.contains("esa reunion")
                        || q.contains("ese acta")
                        || q.contains("esa acta")
                        || q.contains("esa fecha");
        boolean actaMeetingField =
                participants
                        || q.contains("presidente")
                        || q.contains("presidenta")
                        || q.contains("secretari")
                        || q.contains("duración")
                        || q.contains("duracion")
                        || q.contains("lugar")
                        || q.contains("temas")
                        || q.contains("acuerdos")
                        || q.contains("acuerdo")
                        || q.contains("orden del día")
                        || q.contains("orden del dia")
                        || demonstrativeFollowUp
                        || (q.contains("los participantes") && !hasExplicitDateInText(q))
                        || ConversationFollowUpResolver.isActaStructuredFieldFollowUp(q);
        boolean asksPresident =
                q.contains("presidente") || q.contains("presidió") || q.contains("presidio");
        boolean asksWho = q.contains("quién") || q.contains("quien");
        if (asksPresident && asksWho) {
            return true;
        }
        return countParticipants
                || pronounFollowUp
                || demonstrativeFollowUp
                || ConversationFollowUpResolver.requiresUniqueAnchorDate(q)
                || (actaMeetingField && (participants || q.contains("presidente")));
    }

    private boolean hasLocalConversationActaAnchor(ExecutionContext ctx) {
        String effective = effectiveQueryForActaGuard(ctx);
        if (hasExplicitDateInText(effective)) {
            return true;
        }
        List<ConversationMemoryTurn> history = historyLoader.loadEligibleHistory(ctx);
        if (history.isEmpty()) {
            return false;
        }
        String lower = effective != null ? effective.toLowerCase(Locale.ROOT) : "";
        if (ConversationFollowUpResolver.requiresUniqueAnchorDate(lower)) {
            return ConversationFollowUpResolver.findUniqueAnchorDate(history).isPresent();
        }
        return ConversationFollowUpResolver.findMostRecentDate(history).isPresent();
    }

    static String effectiveQueryForActaGuard(ExecutionContext ctx) {
        String effective = ctx.effectivePlanningInputText();
        if (effective != null && !effective.isBlank()) {
            return effective;
        }
        return ctx.userQuery();
    }

    private static boolean hasExplicitDateInText(String text) {
        return ActaFieldAnchorHeuristics.hasExplicitDateInText(text);
    }
}
