package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.language.QueryLanguagePolicy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects LLM grounding-prefix fragments (e.g. literal {@code Based}) and replaces them with safe abstention
 * or a minimal grounded fallback when retrieved sources are available.
 */
public final class PrefixOnlyAnswerGuard {

    private static final Pattern SOURCE_FOOTER =
            Pattern.compile(
                    "(?s)\\n\\n(?:Fuentes(?:\\s+consultadas)?|Sources?)\\s*:.+$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern GROUNDING_PREFIX_ONLY =
            Pattern.compile(
                    "^(?i)(?:Based(?:\\s+on(?:\\s+the)?)?|According(?:\\s+to(?:\\s+the)?)?|"
                            + "Según(?:\\s+(?:las?\\s+)?(?:fuentes|evidencia|contexto|documentos|actas|lo)?)?|"
                            + "Basado\\s+en(?:\\s+lo)?|En\\s+base\\s+a)\\s*\\.?$");

    private static final Pattern SUBSTANTIVE_TOKEN =
            Pattern.compile(
                    "(?i)\\b(?:acta|reuni[oó]n|c[aá]mara|videovigil|asistent|particip|president|secretari|"
                            + "calefac|decisi[oó]n|\\.pdf|20\\d{2}|no\\s+(?:consta|hay|se\\s+encontr))\\b");

    private PrefixOnlyAnswerGuard() {}

    public static boolean isPrefixOnlyFragment(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String core = stripSourceFooter(text.trim());
        if (core.equalsIgnoreCase("Based")) {
            return true;
        }
        if (GROUNDING_PREFIX_ONLY.matcher(core).matches()) {
            return true;
        }
        if (core.length() <= 48
                && core.matches("(?i)(?:Based|Según|According|Basado|En\\s+base).*")
                && !SUBSTANTIVE_TOKEN.matcher(core).find()) {
            return true;
        }
        return false;
    }

    public static String resolve(String answer, String query, List<Map<String, Object>> responseSources) {
        if (!isPrefixOnlyFragment(answer)) {
            return answer;
        }
        boolean spanish = QueryLanguagePolicy.looksSpanish(query != null ? query : answer);
        Optional<String> grounded = groundedFallbackFromSources(responseSources, spanish);
        if (grounded.isPresent()) {
            return grounded.get();
        }
        return RuntimeAnswerPrompts.insufficientDocumentContextMessageFor(query);
    }

    public static String resolveDraft(String draft, String query, String contextText) {
        if (!isPrefixOnlyFragment(draft)) {
            return draft;
        }
        return RuntimeAnswerPrompts.insufficientDocumentContextMessageFor(query);
    }

    static String stripSourceFooter(String text) {
        if (text == null) {
            return "";
        }
        return SOURCE_FOOTER.matcher(text).replaceAll("").trim();
    }

    private static Optional<String> groundedFallbackFromSources(
            List<Map<String, Object>> responseSources, boolean spanish) {
        List<String> refs = extractSourceRefs(responseSources);
        if (refs.isEmpty()) {
            return Optional.empty();
        }
        if (spanish) {
            return Optional.of(
                    "Según las actas recuperadas, la información relevante aparece en: "
                            + String.join(", ", refs)
                            + ".");
        }
        return Optional.of(
                "According to the retrieved minutes, relevant information appears in: "
                        + String.join(", ", refs)
                        + ".");
    }

    private static List<String> extractSourceRefs(List<Map<String, Object>> responseSources) {
        if (responseSources == null || responseSources.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> refs = new ArrayList<>();
        for (Map<String, Object> source : responseSources) {
            if (source == null) {
                continue;
            }
            Object filename = source.get("filename");
            if (filename == null) {
                filename = source.get("documentId");
            }
            if (filename != null && !String.valueOf(filename).isBlank()) {
                String ref = String.valueOf(filename).trim();
                if (seen.add(ref)) {
                    refs.add(ref);
                }
            }
            if (refs.size() >= 5) {
                break;
            }
        }
        return refs;
    }
}
