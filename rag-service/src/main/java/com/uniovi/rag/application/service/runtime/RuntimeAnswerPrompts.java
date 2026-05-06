package com.uniovi.rag.application.service.runtime;

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
            "No he encontrado información suficiente en los documentos del proyecto para responder con seguridad.";

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

    private static final String DOCUMENT_BOUND_TEMPLATE =
            """
            You are a helpful assistant. The user is asking a question that MUST be answered using ONLY the provided document context.

            CRITICAL RULES:
            1. You MUST NOT use general world knowledge. Use ONLY the context below.
            2. If the context is empty, reply with EXACTLY this sentence (and nothing else):
               "%s"
            3. If the context is not empty but does NOT contain enough evidence to answer (for example, the user asked for a specific date but the retrieved documents have a different date), do NOT claim there are no documents.
               Instead, explain what is missing or mismatched, and mention the closest retrieved documents (file names and dates) ONLY if they appear in the context.
            4. Do not invent names, dates, counts, presidents, assistants, or meeting facts not present in the context.
            5. Answer in the SAME LANGUAGE as the user's question.

            %s
            <Question> %s </Question>
            <Context> %s </Context>

            Answer now:
            """;

    private RuntimeAnswerPrompts() {
    }

    public static String ragUserTurn(String rawQuestion, String contextBlock) {
        return ragUserTurn(rawQuestion, contextBlock, false, null);
    }

    public static String ragUserTurn(String rawQuestion, String contextBlock, boolean documentBound) {
        return ragUserTurn(rawQuestion, contextBlock, documentBound, null);
    }

    /**
     * Builds the user-turn prompt for the orchestrated runtime.
     *
     * @param answerPlanBlock optional, safe plan block (e.g. {@code <AnswerPlan>...}).
     */
    public static String ragUserTurn(
            String rawQuestion,
            String contextBlock,
            boolean documentBound,
            String answerPlanBlock) {
        String q = rawQuestion != null ? rawQuestion : "";
        String c = contextBlock != null ? contextBlock : "";
        String plan = answerPlanBlock != null && !answerPlanBlock.isBlank() ? answerPlanBlock.trim() : "";
        String planSection = plan.isBlank() ? "" : plan + "\n";
        if (documentBound) {
            String abstain = insufficientDocumentContextMessageFor(q);
            return String.format(DOCUMENT_BOUND_TEMPLATE, abstain, planSection, q, c);
        }
        return String.format(GENERAL_TEMPLATE, planSection, q, c);
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
