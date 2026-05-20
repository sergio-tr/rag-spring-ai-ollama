package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User-turn prompt templates for runtime workflows (single LLM call path).
 */
public final class RuntimeAnswerPrompts {

    public static final String INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES =
            "No consta en las fuentes disponibles información suficiente para responder con seguridad.";

    public static final String INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_EN =
            "I could not find enough information in the project documents to answer confidently.";

    public static final String DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_ES =
            "Para responder a esa pregunta necesito usar los documentos del proyecto, pero esta conversación no tiene retrieval activo o no hay documentos disponibles.";

    public static final String DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_EN =
            "To answer that question I need to use the project documents, but retrieval is not active or no documents are available for this chat.";

    private static final String GENERAL_TEMPLATE =
            """
            You are a helpful assistant. Retrieved fragments from a meeting-minutes database are provided below when available.

            PRIORITY — answer the user's question:
            - If the question is general (jokes, definitions, chat, etc.) you may answer directly using general knowledge in the user's language.
            - If the question is about the documents, base factual claims on the context only; never invent acta-specific facts not in the context.

            RULES for document-specific answers:
            1. NEVER invent names, dates, places, actas, or facts not in the context when answering about meetings.
            2. Answer in the SAME LANGUAGE as the user's question.
            3. Be concise. Do not add unnecessary headers.

            %s
            <Question> %s </Question>
            <Context> %s </Context>

            Provide your direct answer now (in the same language as the question):
            """;

    /**
     * Attempt-first grounded template: never substitute a blanket abstention when context is non-empty.
     */
    private static final String ATTEMPT_DOCUMENT_TEMPLATE =
            """
            You are a helpful assistant answering questions about meeting minutes / project documents.

            The CONTEXT below contains retrieved fragments; they may be partial, noisy, or not an exact match for every constraint in the question.

            CRITICAL RULES:
            1. Base factual claims ONLY on the CONTEXT. Do not invent acta-specific names, dates, attendees, or facts not supported by the CONTEXT.
            2. If the question asks for a specific date/acta, answer only from sources for that exact date/acta.
            3. If the exact date/acta is not supported by the CONTEXT, say that no matching acta/source was found; you may mention nearby actas only as alternatives, not as the answer.
            4. Do not mix actas from different dates as if they were one document.
            5. For roles such as president or secretary, answer only when the role and person are explicit in the CONTEXT; otherwise say the evidence does not support that field.
            6. If evidence is partial, answer only the supported part and name the limitation.
            7. Answer in the SAME LANGUAGE as the user's question.
            8. Be concise.

            %s
            <Question> %s </Question>
            <Context> %s </Context>

            Answer now:
            """;

    private static final String STRICT_DOCUMENT_TEMPLATE =
            """
            You are a helpful assistant. The user asks about project/meeting documents; use ONLY the CONTEXT below.

            CRITICAL RULES:
            1. Do not use general world knowledge for document-specific facts.
            2. If the question asks for a specific date/acta, answer only from sources for that exact date/acta.
            3. If the exact date/acta is not supported, abstain clearly and optionally mention nearby actas as alternatives only.
            4. Never invent names, dates, counts, roles, attendees, or secretary/president fields not present in the CONTEXT.
            5. Do not merge evidence from different actas unless the user asks for comparison.
            6. Answer in the SAME LANGUAGE as the user's question.

            %s
            <Question> %s </Question>
            <Context> %s </Context>

            Answer now:
            """;

    private static final String NEGATIVE_DOCUMENT_TEMPLATE =
            """
            You are a helpful assistant. The CONTEXT below may have been filtered or compressed after retrieval; treat it as the surviving evidence.

            CRITICAL RULES:
            1. Base factual claims ONLY on the CONTEXT; do not invent document facts.
            2. If the CONTEXT is non-empty, answer from what remains; express uncertainty where evidence is thin.
            3. If an exact match for the question is missing but related fragments exist, explain that gap and give the best partial summary.
            4. Answer in the SAME LANGUAGE as the user's question.

            %s
            <Question> %s </Question>
            <Context> %s </Context>

            Answer now:
            """;

    private static final String DIRECT_BASELINE_USER_TEMPLATE =
            """
            You are a helpful assistant. For this turn you are NOT using retrieved project documents (retrieval is disabled).
            Answer the user's question directly with general knowledge where appropriate.
            If the question is specifically about internal meeting minutes or uploaded documents for this project, clearly state that you are not consulting those documents.

            %s
            <Question> %s </Question>

            Answer now:
            """;

    private RuntimeAnswerPrompts() {
    }

    public static String ragUserTurn(String rawQuestion, String contextBlock) {
        return ragUserTurn(rawQuestion, contextBlock, AnswerGroundingPolicy.ATTEMPT_WITH_CONTEXT, false, Optional.empty(), null);
    }

    public static String ragUserTurn(String rawQuestion, String contextBlock, boolean documentBound) {
        return ragUserTurn(
                rawQuestion, contextBlock, AnswerGroundingPolicy.ATTEMPT_WITH_CONTEXT, documentBound, Optional.empty(), null);
    }

    public static String directBaselineUserTurn(String rawQuestion, String answerPlanBlock) {
        String q = rawQuestion != null ? rawQuestion : "";
        String plan = answerPlanBlock != null && !answerPlanBlock.isBlank() ? answerPlanBlock.trim() : "";
        String planSection = plan.isBlank() ? "" : plan + "\n";
        return String.format(DIRECT_BASELINE_USER_TEMPLATE, planSection, q);
    }

    /**
     * When {@code promptContextText} is blank (e.g. compression dropped all), rebuild a minimal context from candidates
     * so generation can still use non-empty evidence when sources exist.
     */
    public static String effectivePromptContext(String promptContextText, List<RetrievalCandidate> candidates) {
        if (promptContextText != null && !promptContextText.isBlank()) {
            return promptContextText;
        }
        return fallbackContextFromCandidates(candidates, 24_000);
    }

    /**
     * Builds LLM context for date-bound questions from {@linkplain DateGroundingSupport#candidatesForGroundedAnswer
     * grounded candidates} only, so wrong-year actas are not injected when an exact date was requested.
     */
    public static String effectivePromptContextForDateGrounding(
            String promptContextText,
            List<RetrievalCandidate> allCandidates,
            DateGroundingSupport.DateGroundingDecision decision) {
        if (decision == null || decision.requestedDate() == null) {
            return effectivePromptContext(promptContextText, allCandidates);
        }
        List<RetrievalCandidate> grounded =
                DateGroundingSupport.candidatesForGroundedAnswer(decision, allCandidates);
        String rebuilt = fallbackContextFromCandidates(grounded, 24_000);
        if (!rebuilt.isBlank()) {
            return rebuilt;
        }
        return effectivePromptContext(promptContextText, grounded);
    }

    public static String fallbackContextFromCandidates(List<RetrievalCandidate> candidates, int maxChars) {
        if (candidates == null || candidates.isEmpty() || maxChars <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int budget = maxChars;
        for (RetrievalCandidate c : candidates) {
            if (budget <= 0) {
                break;
            }
            String fn = safeFilename(c);
            String body = c != null && c.content() != null ? c.content() : "";
            String header = fn.isBlank() ? "" : "File: " + fn + "\n";
            String chunk = header + body + "\n\n---\n\n";
            if (chunk.length() > budget) {
                chunk = chunk.substring(0, budget);
            }
            sb.append(chunk);
            budget -= chunk.length();
        }
        return sb.toString().trim();
    }

    /**
     * Builds the user-turn prompt for the orchestrated runtime.
     *
     * @param dateMismatchNotice when present, prepended to the context block so the model explains mismatch and still answers.
     * @param answerPlanBlock optional, safe plan block (e.g. {@code <AnswerPlan>...}).
     */
    public static String ragUserTurn(
            String rawQuestion,
            String contextBlock,
            AnswerGroundingPolicy policy,
            boolean documentScopedQuestion,
            Optional<String> dateMismatchNotice,
            String answerPlanBlock) {
        String q = rawQuestion != null ? rawQuestion : "";
        String c0 = contextBlock != null ? contextBlock : "";
        String plan = answerPlanBlock != null && !answerPlanBlock.isBlank() ? answerPlanBlock.trim() : "";
        String planSection = plan.isBlank() ? "" : plan + "\n";

        if (!documentScopedQuestion) {
            return String.format(GENERAL_TEMPLATE, planSection, q, c0);
        }

        String notice =
                dateMismatchNotice.filter(s -> s != null && !s.isBlank()).map(s -> s.trim() + "\n\n").orElse("");
        String contextCombined = notice + c0;

        AnswerGroundingPolicy p = policy != null ? policy : AnswerGroundingPolicy.ATTEMPT_WITH_CONTEXT;
        return switch (p) {
            case DIRECT_UNGROUNDED_BASELINE -> String.format(DIRECT_BASELINE_USER_TEMPLATE, planSection, q);
            case CORPUS_GROUNDED_BASELINE ->
                    String.format(ATTEMPT_DOCUMENT_TEMPLATE, planSection, q, contextCombined);
            case STRICT_GROUNDED -> String.format(STRICT_DOCUMENT_TEMPLATE, planSection, q, contextCombined);
            case NEGATIVE_GROUNDED -> String.format(NEGATIVE_DOCUMENT_TEMPLATE, planSection, q, contextCombined);
            case ATTEMPT_WITH_CONTEXT -> String.format(ATTEMPT_DOCUMENT_TEMPLATE, planSection, q, contextCombined);
        };
    }

    /**
     * Telemetry stage merged into {@link com.uniovi.rag.domain.runtime.engine.ExecutionTrace}.
     */
    public static ExecutionStageTrace runtimeAnswerMetaStage(
            AnswerGroundingPolicy policy,
            int contextCharCount,
            int sourceCount,
            boolean abstentionTriggered,
            String abstentionReason) {
        String reason = abstentionReason != null ? abstentionReason.replace('\n', ' ').trim() : "";
        String msg =
                "policy="
                        + (policy != null ? policy.name() : "")
                        + " contextChars="
                        + contextCharCount
                        + " sourceCount="
                        + sourceCount
                        + " abstention="
                        + abstentionTriggered
                        + " reason="
                        + reason;
        return new ExecutionStageTrace("runtime_answer_meta", 0L, ExecutionStageOutcome.SUCCESS, msg);
    }

    public static boolean requiresStrictDocumentGrounding(String rawQuestion) {
        return DocumentBoundQuestionPolicy.isDocumentBoundQuestion(rawQuestion);
    }

    public static String insufficientDocumentContextMessageFor(String rawQuestion) {
        if (looksSpanish(rawQuestion)) {
            return INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES;
        }
        return INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_EN;
    }

    /**
     * Best-effort grounded fallback for document-bound date queries when retrieval returns sources but none match
     * the requested date. Returns empty when no parseable date is requested or when an exact match exists.
     */
    public static Optional<String> groundedDateMismatchMessageFor(
            String rawQuestion,
            List<RetrievalCandidate> finalCandidates
    ) {
        if (rawQuestion == null || rawQuestion.isBlank() || finalCandidates == null || finalCandidates.isEmpty()) {
            return Optional.empty();
        }
        Optional<RequestedDate> requested = RequestedDate.tryParseFromQuestion(rawQuestion);
        if (requested.isEmpty()) {
            return Optional.empty();
        }

        List<CandidateDateEvidence> evidence = extractCandidateDateEvidence(finalCandidates);
        boolean anyExact = evidence.stream().anyMatch(e -> e.canonicalDate() != null
                && e.canonicalDate().equals(requested.get().canonicalDate()));
        if (anyExact) {
            return Optional.empty();
        }

        Map<String, String> docToDate = new LinkedHashMap<>();
        for (CandidateDateEvidence e : evidence) {
            if (e.filename() == null || e.filename().isBlank()) {
                continue;
            }
            // Keep first seen per doc (stable).
            docToDate.putIfAbsent(e.filename(), e.displayDate() != null ? e.displayDate() : "");
        }

        String reqDisplay = requested.get().display();
        boolean es = looksSpanish(rawQuestion);
        if (docToDate.isEmpty()) {
            // We have sources, but couldn't extract any date evidence from the text.
            if (es) {
                return Optional.of(
                        "He recuperado documentos relacionados, pero no encuentro en los fragmentos recuperados una coincidencia exacta para la fecha solicitada ("
                                + reqDisplay
                                + "). Si quieres, dime el nombre exacto del documento o amplia el rango de fechas (por ejemplo, 2025–2026).");
            }
            return Optional.of(
                    "I retrieved related documents, but I can't find an exact match for the requested date ("
                            + reqDisplay
                            + ") in the retrieved fragments. If you want, tell me the exact document name or widen the date range (e.g., 2025–2026).");
        }

        List<String> lines = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> e : docToDate.entrySet()) {
            if (i >= 3) {
                break;
            }
            String fn = e.getKey();
            String d = e.getValue();
            if (d == null || d.isBlank()) {
                lines.add("- " + fn);
            } else {
                lines.add("- " + fn + " (" + d + ")");
            }
            i++;
        }

        if (es) {
            return Optional.of(
                    "No encuentro un acta con fecha " + reqDisplay + " en los documentos recuperados.\n\n"
                            + "Los documentos más cercanos recuperados son:\n"
                            + String.join("\n", lines)
                            + "\n\n"
                            + "Si quieres, confirma la fecha o dime cuál de estos documentos quieres que resuma.");
        }
        return Optional.of(
                "I cannot find minutes dated " + reqDisplay + " in the retrieved documents.\n\n"
                        + "The closest retrieved documents are:\n"
                        + String.join("\n", lines)
                        + "\n\n"
                        + "If you want, confirm the date or tell me which of these documents you want summarized.");
    }

    public static String documentBoundRequiresRetrievalMessageFor(String rawQuestion) {
        if (looksSpanish(rawQuestion)) {
            return DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_ES;
        }
        return DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_EN;
    }

    private static boolean looksSpanish(String rawQuestion) {
        if (rawQuestion == null) {
            return true;
        }
        String q = rawQuestion.toLowerCase();
        return q.contains("¿")
                || q.contains("¡")
                || q.contains("acta")
                || q.contains("reunión")
                || q.contains("reunion")
                || q.contains("documento")
                || q.contains("asistente")
                || q.contains("presidente")
                || q.contains("duración")
                || q.contains("duracion");
    }

    private static List<CandidateDateEvidence> extractCandidateDateEvidence(List<RetrievalCandidate> candidates) {
        List<CandidateDateEvidence> out = new ArrayList<>();
        for (RetrievalCandidate c : candidates) {
            String filename = safeFilename(c);
            String content = c != null ? c.content() : "";
            Optional<ExtractedDate> extracted = ExtractedDate.tryExtractFromText(content);
            if (extracted.isPresent()) {
                out.add(new CandidateDateEvidence(filename, extracted.get().display(), extracted.get().canonicalDate()));
            } else {
                out.add(new CandidateDateEvidence(filename, "", null));
            }
        }
        return out;
    }

    private static String safeFilename(RetrievalCandidate c) {
        if (c == null || c.metadata() == null) {
            return "";
        }
        Object v = c.metadata().get("filename");
        if (v == null) {
            return "";
        }
        return String.valueOf(v).trim();
    }

    private record CandidateDateEvidence(String filename, String displayDate, String canonicalDate) {}

    private record RequestedDate(String display, String canonicalDate) {
        static Optional<RequestedDate> tryParseFromQuestion(String q) {
            Optional<ExtractedDate> d = ExtractedDate.tryExtractFromText(q);
            return d.map(x -> new RequestedDate(x.display(), x.canonicalDate()));
        }
    }

    private record ExtractedDate(String display, String canonicalDate) {
        private static final Pattern DMY_SLASH = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b");
        // e.g. "25 de febrero de 2025"
        private static final Pattern D_DE_M_DE_Y = Pattern.compile(
                "\\b(\\d{1,2})\\s+de\\s+([a-záéíóúñ]+)\\s+de\\s+(\\d{4})\\b",
                Pattern.CASE_INSENSITIVE);

        static Optional<ExtractedDate> tryExtractFromText(String text) {
            if (text == null || text.isBlank()) {
                return Optional.empty();
            }
            Matcher m1 = DMY_SLASH.matcher(text);
            if (m1.find()) {
                int dd = safeInt(m1.group(1));
                int mm = safeInt(m1.group(2));
                int yyyy = safeInt(m1.group(3));
                String canonical = canonical(yyyy, mm, dd);
                String display = m1.group(0);
                if (!canonical.isBlank()) {
                    return Optional.of(new ExtractedDate(display, canonical));
                }
            }
            Matcher m2 = D_DE_M_DE_Y.matcher(text);
            if (m2.find()) {
                int dd = safeInt(m2.group(1));
                int yyyy = safeInt(m2.group(3));
                int mm = spanishMonthToInt(m2.group(2));
                String canonical = canonical(yyyy, mm, dd);
                String display = m2.group(0);
                if (!canonical.isBlank()) {
                    return Optional.of(new ExtractedDate(display, canonical));
                }
            }
            return Optional.empty();
        }

        private static String canonical(int yyyy, int mm, int dd) {
            if (yyyy < 1900 || yyyy > 3000) {
                return "";
            }
            if (mm < 1 || mm > 12) {
                return "";
            }
            if (dd < 1 || dd > 31) {
                return "";
            }
            return String.format("%04d-%02d-%02d", yyyy, mm, dd);
        }

        private static int safeInt(String s) {
            try {
                return Integer.parseInt(s);
            } catch (Exception ignored) {
                return -1;
            }
        }

        private static int spanishMonthToInt(String raw) {
            if (raw == null) {
                return -1;
            }
            String m = raw.trim().toLowerCase()
                    .replace('á', 'a')
                    .replace('é', 'e')
                    .replace('í', 'i')
                    .replace('ó', 'o')
                    .replace('ú', 'u');
            return switch (m) {
                case "enero" -> 1;
                case "febrero" -> 2;
                case "marzo" -> 3;
                case "abril" -> 4;
                case "mayo" -> 5;
                case "junio" -> 6;
                case "julio" -> 7;
                case "agosto" -> 8;
                case "septiembre", "setiembre" -> 9;
                case "octubre" -> 10;
                case "noviembre" -> 11;
                case "diciembre" -> 12;
                default -> -1;
            };
        }
    }
}
