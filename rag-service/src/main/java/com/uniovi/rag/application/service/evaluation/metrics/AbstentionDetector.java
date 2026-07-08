package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.runtime.RuntimeAnswerPrompts;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Deterministic abstention detection from runtime metadata and normalized answer text. */
public final class AbstentionDetector {

    public static final String KEY_ABSTAINED = "abstained";
    public static final String KEY_ABSTENTION_REASON = "abstentionReason";
    public static final String KEY_ABSTENTION_SOURCE = "abstentionSource";

    private static final List<String> PHRASE_PATTERNS =
            List.of(
                    normalize(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_EN),
                    normalize(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES),
                    normalize(RuntimeAnswerPrompts.DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_EN),
                    normalize(RuntimeAnswerPrompts.DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_ES),
                    "insufficient evidence",
                    "insufficient context",
                    "not enough information",
                    "could not find enough information",
                    "cannot answer",
                    "unable to answer",
                    "no evidence",
                    "no consta en las fuentes",
                    "informacion suficiente",
                    "información suficiente");

    private static final List<Pattern> EXTENDED_PATTERNS =
            List.of(
                    Pattern.compile("no puedo (proporcionar|encontrar|responder|determinar|confirmar|indicar|detallar|precisar|acceder)"),
                    Pattern.compile("no (encuentro|dispongo|tengo|cuento) (con )?(informacion|acceso|datos|constancia|contexto)"),
                    Pattern.compile("no hay informacion"),
                    Pattern.compile("no es posible (determinar|responder|proporcionar|saber|confirmar)"),
                    Pattern.compile("no aparece en (el|los|la|las)? ?(contexto|documento|acta|actas|fuentes)"),
                    Pattern.compile("no figura en"),
                    Pattern.compile("i (cannot|can't|couldn't|could not|don't|do not|didn't) (find|provide|know|answer|determine|access)"),
                    Pattern.compile("i don'?t have (access|enough|sufficient)"),
                    Pattern.compile("no information (is )?available"),
                    Pattern.compile("not (mentioned|found|available|stated|specified) in the (context|provided|document|sources)"),
                    Pattern.compile("unable to (find|provide|determine|answer)"));

    private AbstentionDetector() {}

    public static void detectAndMerge(Map<String, Object> metrics, String actualAnswer) {
        if (metrics == null) {
            return;
        }
        Result result = detect(metrics, actualAnswer);
        metrics.put(KEY_ABSTAINED, result.abstained());
        if (!result.reason().isBlank()) {
            metrics.put(KEY_ABSTENTION_REASON, result.reason());
        }
        metrics.put(KEY_ABSTENTION_SOURCE, result.source());
    }

    public static Result detect(Map<String, Object> metrics, String actualAnswer) {
        Map<String, Object> mp = metrics != null ? metrics : Map.of();
        if (runtimeAbstention(mp)) {
            String reason = firstNonBlank(str(mp.get("abstentionReason")), str(mp.get("abstentionReasonCode")));
            return new Result(true, reason.isBlank() ? "runtime_metadata" : reason, "RUNTIME_METADATA");
        }
        String answer = actualAnswer != null ? actualAnswer : "";
        if (answer.isBlank()) {
            return new Result(false, "empty_answer", "EMPTY_ANSWER");
        }
        String normalized = normalize(answer);
        for (String phrase : PHRASE_PATTERNS) {
            if (!phrase.isBlank() && normalized.contains(phrase)) {
                return new Result(true, "phrase_match", "ANSWER_TEXT");
            }
        }
        for (Pattern pattern : EXTENDED_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return new Result(true, "phrase_match_extended", "ANSWER_TEXT_PATTERN");
            }
        }
        return new Result(false, "", "NONE");
    }

    private static boolean runtimeAbstention(Map<String, Object> mp) {
        return bool(mp.get("abstentionTriggered"));
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String n = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return n.replaceAll("\\s+", " ").trim();
    }

    private static boolean bool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null ? b : "";
    }

    public record Result(boolean abstained, String reason, String source) {}
}
