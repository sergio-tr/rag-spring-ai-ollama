package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.util.QueryDateSupport;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Final presentation pass: Spanish natural phrasing, structured list/count/summary shape, source
 * references, unavailable explanations, and removal of internal route/debug labels.
 */
public final class FinalAnswerSynthesizer {

    private static final int UNICODE_CANON = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ;

    private FinalAnswerSynthesizer() {}

    private static final Pattern JUDGE_FORMAT_LEAK =
            Pattern.compile(
                    "(?im)^\\s*Answer\\s*:\\s*(?:YES|NO)\\s*$|(?im)^\\s*Explanation\\s*:.*+$|(?im)^\\s*FEEDBACK\\s*:.*+$");
    private static final Pattern ACTA_TOPIC_VAGUE =
            Pattern.compile(
                    "(?:en el acta correspondiente|en la reunión específica del documento consultado|documento consultado)",
                    UNICODE_CANON);
    private static final Pattern INTERNAL_LABEL =
            Pattern.compile(
                    "\\b(?:PARENT_P\\d+|PARENT_P[A-Z_]+|baseline_floor[\\w:]*|RETRIEVAL_WORKFLOW_ROUTE|DETERMINISTIC_TOOL_ROUTE|FUNCTION_CALLING_ROUTE|ADVISOR_ROUTE|deterministic-tool|function-calling|topic_not_in_context|not_in_context|function_sentinel_abstention|native_not_constraint_complete|advanced_preset_parent_floor|outcome=\\w+|routeKind=\\w+)\\b",
                    UNICODE_CANON);
    private static final Pattern BARE_COUNT = Pattern.compile("^\\s*(\\d{1,4})\\s*\\.?\\s*$", Pattern.CANON_EQ);
    private static final Pattern ACTA_REF = Pattern.compile("(?i)(acta[^\\s,;.]{0,40}\\.pdf)", UNICODE_CANON);
    private static final Pattern TIMING_ONLY_SUMMARY =
            Pattern.compile(
                    "(?:duración|duracion|de \\d{1,2}:\\d{2}|asistentes?|participantes?)",
                    UNICODE_CANON);

    public static RagExecutionResult apply(QueryPlan plan, RagExecutionResult result) {
        if (result == null) {
            return null;
        }
        if (!result.allowPostSynthesisRewrite()) {
            return applySafeTerminalFormatting(plan, result);
        }
        String synthesized = synthesize(plan, result.answerText(), result.responseSources());
        if (synthesized.equals(result.answerText())) {
            return result;
        }
        return new RagExecutionResult(
                synthesized,
                result.workflowName(),
                result.retrievalUsed(),
                result.metadataUsed(),
                result.usedResolvedConfigSnapshotId(),
                result.usedConfigHash(),
                result.usedKnowledgeSnapshotIds(),
                result.executionTrace(),
                result.toolUsedLabel(),
                result.resolvedQueryType(),
                result.usedTool(),
                result.workflowStageTraces(),
                result.retrievalDiagnostics(),
                result.responseSources(),
                result.answerFinality());
    }

    public static RagExecutionResult applySafeTerminalFormatting(QueryPlan plan, RagExecutionResult result) {
        if (result == null) {
            return null;
        }
        String formatted = synthesizeSafeTerminal(plan, result.answerText(), result.responseSources());
        if (formatted.equals(result.answerText())) {
            return result;
        }
        return new RagExecutionResult(
                formatted,
                result.workflowName(),
                result.retrievalUsed(),
                result.metadataUsed(),
                result.usedResolvedConfigSnapshotId(),
                result.usedConfigHash(),
                result.usedKnowledgeSnapshotIds(),
                result.executionTrace(),
                result.toolUsedLabel(),
                result.resolvedQueryType(),
                result.usedTool(),
                result.workflowStageTraces(),
                result.retrievalDiagnostics(),
                result.responseSources(),
                result.answerFinality());
    }

    public static String synthesizeSafeTerminal(QueryPlan plan, String answerText, List<Map<String, Object>> responseSources) {
        if (answerText == null || answerText.isBlank()) {
            return answerText;
        }
        String cleaned = stripJudgeFormatLeakage(answerText);
        cleaned = stripInternalLabels(cleaned);
        cleaned = ReasoningBlockSanitizer.stripReasoningBlocks(cleaned);
        cleaned = FinalAnswerStubSanitizer.sanitizeForUser(plan, cleaned, responseSources);
        String query = extractQueryText(plan);
        cleaned = correctDateDenialAgainstSources(query, cleaned, responseSources);
        cleaned = normalizeSafeSpanishPunctuation(cleaned);
        cleaned = ensureSentenceStart(cleaned);
        cleaned = FinalAnswerMarkdownSanitizer.sanitize(cleaned);
        cleaned = PartialEvidenceAnswerSupport.enrichIfPartial(plan, cleaned, responseSources);
        cleaned = PrefixOnlyAnswerGuard.resolve(cleaned, query, responseSources);
        return cleaned.trim();
    }

    private static String normalizeSafeSpanishPunctuation(String text) {
        return MarkdownAnswerFormatter.collapseHorizontalWhitespacePreservingNewlines(text)
                .replaceAll("(?m) \\.", ".")
                .replaceAll("(?m) ,", ",")
                .trim();
    }

    public static String synthesize(QueryPlan plan, String answerText, List<Map<String, Object>> responseSources) {
        if (answerText == null || answerText.isBlank()) {
            return answerText;
        }
        String query = extractQueryText(plan);
        boolean spanish = RuntimeAnswerPrompts.requiresStrictDocumentGrounding(query)
                || looksSpanish(query != null ? query : answerText);

        String cleaned = stripJudgeFormatLeakage(answerText);
        cleaned = stripInternalLabels(cleaned);
        cleaned = ReasoningBlockSanitizer.stripReasoningBlocks(cleaned);
        cleaned = FinalAnswerStubSanitizer.sanitizeForUser(plan, cleaned, responseSources);
        cleaned = correctDateDenialAgainstSources(query, cleaned, responseSources);
        cleaned = normalizeUnavailableMessage(cleaned, spanish);
        cleaned = structureByQueryType(plan, cleaned, spanish);
        cleaned = enforceMultiMatchEnumeration(plan, cleaned, responseSources, spanish);
        cleaned = enforceActaIdentifierContract(plan, cleaned, responseSources, spanish);
        cleaned = appendSourceReferencesIfMissing(cleaned, responseSources, spanish);
        cleaned = PartialEvidenceAnswerSupport.enrichIfPartial(plan, cleaned, responseSources);
        cleaned = ensureSentenceStart(cleaned);
        cleaned = FinalAnswerMarkdownSanitizer.sanitize(cleaned);
        cleaned = PrefixOnlyAnswerGuard.resolve(cleaned, query, responseSources);
        return cleaned.trim();
    }

    public static String sanitizeJudgeLeakage(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return stripJudgeFormatLeakage(text);
    }

    private static String stripJudgeFormatLeakage(String text) {
        String out = JUDGE_FORMAT_LEAK.matcher(text).replaceAll("").trim();
        out = out.replaceAll("(?im)^\\s*Answer\\s*:\\s*(?:YES|NO)\\s*\\n?", "").trim();
        return out;
    }

    private static final Pattern DATE_IN_TEXT = QueryDateSupport.NUMERIC_OR_LONG_SPANISH_DATE;

    private static String enforceMultiMatchEnumeration(
            QueryPlan plan, String text, List<Map<String, Object>> responseSources, boolean spanish) {
        if (text == null || text.isBlank() || responseSources == null || responseSources.size() < 2) {
            return text;
        }
        Optional<QueryType> qt = plan != null ? plan.classifierQueryType() : Optional.empty();
        if (qt.isEmpty()
                || (qt.get() != QueryType.FILTER_AND_LIST
                        && qt.get() != QueryType.COUNT_DOCUMENTS
                        && qt.get() != QueryType.COUNT_AND_EXPLAIN)) {
            return text;
        }
        List<String> sourceRefs = extractAllSourceRefs(responseSources);
        if (sourceRefs.size() < 2) {
            return text;
        }
        int mentionedInAnswer = countDistinctActaMentions(text, sourceRefs);
        if (mentionedInAnswer >= sourceRefs.size()) {
            return text;
        }
        if (spanish) {
            StringBuilder sb = new StringBuilder();
            sb.append("Se encontraron ").append(sourceRefs.size()).append(" actas:\n\n");
            for (String ref : sourceRefs) {
                sb.append("- ").append(ref).append('\n');
            }
            return sb.toString().trim();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(sourceRefs.size()).append(" matching actas:\n\n");
        for (String ref : sourceRefs) {
            sb.append("- ").append(ref).append('\n');
        }
        return sb.toString().trim();
    }

    private static List<String> extractAllSourceRefs(List<Map<String, Object>> responseSources) {
        List<String> refs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> src : responseSources) {
            if (src == null) {
                continue;
            }
            Object fn = src.get("filename");
            if (fn == null) {
                fn = src.get("documentId");
            }
            if (fn != null && !String.valueOf(fn).isBlank()) {
                String ref = String.valueOf(fn).trim();
                if (seen.add(ref)) {
                    refs.add(ref);
                }
            }
        }
        return refs;
    }

    private static int countDistinctActaMentions(String text, List<String> sourceRefs) {
        int count = 0;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String ref : sourceRefs) {
            if (lower.contains(ref.toLowerCase(Locale.ROOT))) {
                count++;
                continue;
            }
            Matcher date = DATE_IN_TEXT.matcher(ref);
            if (date.find() && lower.contains(date.group().toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    private static String enforceActaIdentifierContract(
            QueryPlan plan, String text, List<Map<String, Object>> responseSources, boolean spanish) {
        if (plan == null || text == null || text.isBlank()) {
            return text;
        }
        String query = plan.rewrittenQueryText() != null ? plan.rewrittenQueryText() : plan.normalizedQueryText();
        if (query == null || !query.toLowerCase(Locale.ROOT).contains("en qué acta")
                && !query.toLowerCase(Locale.ROOT).contains("en que acta")) {
            return text;
        }
        if (ACTA_TOPIC_VAGUE.matcher(text).find()) {
            List<String> refs = extractSourceRefs(responseSources);
            if (!refs.isEmpty()) {
                if (spanish) {
                    return "Según las fuentes recuperadas, el tema aparece en " + String.join(", ", refs) + ".";
                }
                return "Based on the retrieved sources, the topic appears in " + String.join(", ", refs) + ".";
            }
        }
        return text;
    }

    private static List<String> extractSourceRefs(List<Map<String, Object>> responseSources) {
        List<String> refs = new ArrayList<>();
        if (responseSources == null) {
            return refs;
        }
        for (Map<String, Object> src : responseSources) {
            if (src == null) {
                continue;
            }
            Object fn = src.get("filename");
            if (fn != null && !String.valueOf(fn).isBlank()) {
                refs.add(String.valueOf(fn).trim());
            }
            if (refs.size() >= 3) {
                break;
            }
        }
        return refs;
    }

    private static String stripInternalLabels(String text) {
        String out = INTERNAL_LABEL.matcher(text).replaceAll("").trim();
        out = MarkdownAnswerFormatter.collapseHorizontalWhitespacePreservingNewlines(out).trim();
        out = out.replaceAll("(?m) \\.", ".").replaceAll("(?m) ,", ",");
        out = out.replaceAll("(?m)^[:;\\-]+\\s*", "").trim();
        return out;
    }

    private static String normalizeUnavailableMessage(String text, boolean spanish) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (!spanish) {
            return text;
        }
        if (lower.contains("no consta") || lower.contains("no se encontr") || lower.contains("no hay")) {
            if (!lower.contains("porque") && !lower.contains("motivo") && !lower.contains("fuentes")) {
                if (lower.matches(".*\\bno consta\\b.*") && text.length() < 80) {
                    return RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES;
                }
            }
            return text;
        }
        if (lower.contains("i could not find") || lower.contains("no information found")) {
            return RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES;
        }
        return text;
    }

    private static String structureByQueryType(QueryPlan plan, String text, boolean spanish) {
        if (plan == null || text == null || text.isBlank()) {
            return text;
        }
        Optional<QueryType> qt = plan.classifierQueryType();
        if (qt.isEmpty()) {
            return text;
        }
        return switch (qt.get()) {
            case COUNT_DOCUMENTS, COUNT_AND_EXPLAIN -> formatCountAnswer(text, spanish);
            case FILTER_AND_LIST -> formatListAnswer(text);
            case GET_FIELD -> text;
            case SUMMARIZE_MEETING -> enrichSummaryIfTimingOnly(text, spanish);
            default -> text;
        };
    }

    private static String formatCountAnswer(String text, boolean spanish) {
        if (spanish && isStructuredNegativeCountAnswer(text)) {
            return text;
        }
        Matcher bare = BARE_COUNT.matcher(text.trim());
        if (bare.matches() && spanish) {
            String n = bare.group(1);
            return "En total son " + n + " actas. Indica el criterio si necesitas el detalle de cada una.";
        }
        if (spanish
                && text.matches(".*\\b\\d+\\b.*")
                && !text.toLowerCase(Locale.ROOT).contains("acta")
                && text.length() < 120) {
            Matcher digit = Pattern.compile("\\b(\\d{1,4})\\b").matcher(text);
            if (digit.find()) {
                return "Se encontraron " + digit.group(1) + " actas. " + text.trim();
            }
        }
        return text;
    }

    private static boolean isStructuredNegativeCountAnswer(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("no existen actas")
                || lower.contains("no hay reuniones registradas en el año")
                || lower.contains("no hay ninguna acta")
                || lower.contains("no se encontraron actas")
                || lower.contains("ninguna acta");
    }

    private static String formatListAnswer(String text) {
        List<String> actas = new ArrayList<>();
        Matcher m = ACTA_REF.matcher(text);
        while (m.find()) {
            actas.add(m.group(1));
        }
        if (actas.size() < 2 || text.contains("\n- ") || text.contains("•")) {
            return text;
        }
        Set<String> unique = new LinkedHashSet<>(actas);
        if (unique.size() < 2) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        int period = text.indexOf('.');
        if (period > 0 && period < text.length() - 1) {
            sb.append(text, 0, period + 1).append("\n\n");
        } else {
            sb.append("Las actas que cumplen el criterio son:\n\n");
        }
        for (String acta : unique) {
            sb.append("- ").append(acta).append('\n');
        }
        return sb.toString().trim();
    }

    private static String enrichSummaryIfTimingOnly(String text, boolean spanish) {
        if (!spanish || text == null || text.length() < 40) {
            return text;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean hasTopics =
                lower.contains("tema")
                        || lower.contains("agenda")
                        || lower.contains("trat")
                        || lower.contains("debat")
                        || lower.contains("acord")
                        || lower.contains("presupuesto")
                        || lower.contains("ascensor")
                        || lower.contains("videovigilancia");
        if (hasTopics || !TIMING_ONLY_SUMMARY.matcher(text).find()) {
            return text;
        }
        return text
                + " En los fragmentos disponibles no se detallan otros temas de agenda aparte de la información de asistencia y horario.";
    }

    private static String appendSourceReferencesIfMissing(
            String text, List<Map<String, Object>> responseSources, boolean spanish) {
        if (text == null
                || text.isBlank()
                || responseSources == null
                || responseSources.isEmpty()) {
            return text;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("fuente:") || lower.contains("fuentes:") || lower.contains("acta")) {
            return text;
        }
        List<String> refs = new ArrayList<>();
        for (Map<String, Object> src : responseSources) {
            if (src == null) {
                continue;
            }
            Object fn = src.get("filename");
            if (fn == null) {
                fn = src.get("documentId");
            }
            if (fn != null && !String.valueOf(fn).isBlank()) {
                refs.add(String.valueOf(fn).trim());
            }
            if (refs.size() >= 3) {
                break;
            }
        }
        if (refs.isEmpty()) {
            return text;
        }
        String label = spanish ? "Fuentes consultadas: " : "Sources: ";
        return text + "\n\n" + label + String.join("; ", refs) + ".";
    }

    private static String ensureSentenceStart(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (Character.isLowerCase(text.charAt(0))) {
            return Character.toUpperCase(text.charAt(0)) + text.substring(1);
        }
        return text;
    }

    private static boolean looksSpanish(String text) {
        if (text == null) {
            return true;
        }
        String q = text.toLowerCase(Locale.ROOT);
        return q.contains("¿")
                || q.contains("acta")
                || q.contains("reunión")
                || q.contains("reunion")
                || q.contains("cuánt")
                || q.contains("cuant")
                || q.contains("presidente")
                || q.contains("asistent");
    }

    private static String extractQueryText(QueryPlan plan) {
        if (plan == null) {
            return "";
        }
        if (plan.rewrittenQueryText() != null && !plan.rewrittenQueryText().isBlank()) {
            return plan.rewrittenQueryText();
        }
        if (plan.normalizedQueryText() != null && !plan.normalizedQueryText().isBlank()) {
            return plan.normalizedQueryText();
        }
        if (plan.rawUserQuery() != null && !plan.rawUserQuery().isBlank()) {
            return plan.rawUserQuery();
        }
        return "";
    }

    private static String correctDateDenialAgainstSources(
            String query, String answer, List<Map<String, Object>> sources) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        if (CorpusDateEvidenceAnswerGuard.answerDeniesDespiteMatchingSources(query, answer, sources)) {
            return CorpusDateEvidenceAnswerGuard.groundedEvidenceReminder(query) + "\n\n" + answer;
        }
        return answer;
    }
}
