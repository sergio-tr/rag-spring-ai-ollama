package com.uniovi.rag.domain.runtime.clarification;

/**
 * Deterministic clarification question kinds (P11). Each maps to exactly one frozen template string.
 */
public enum ClarificationQuestionKind {
    MISSING_DATE,
    MISSING_PERSON,
    MISSING_TOPIC,
    MISSING_LOCATION,
    MULTIPLE_ENTITY_CANDIDATES,
    CONFLICTING_CUES,
    GENERIC_MISSING_INFORMATION;

    /** Frozen template per plan (Spanish for acta-domain date clarification). */
    public String templateText() {
        return switch (this) {
            case MISSING_DATE -> "¿A qué acta o reunión te refieres? Indica la fecha o el documento.";
            case MISSING_PERSON -> "Which person are you referring to?";
            case MISSING_TOPIC -> "Which topic are you referring to?";
            case MISSING_LOCATION -> "Which location are you referring to?";
            case MULTIPLE_ENTITY_CANDIDATES -> "Which of these entities do you mean?";
            case CONFLICTING_CUES -> "Your request contains conflicting cues. Which interpretation should I use?";
            case GENERIC_MISSING_INFORMATION ->
                    "I need one more detail to answer correctly. What specific item should I use?";
        };
    }

    /** All frozen clarification templates for prompt bundle fingerprinting. */
    public static String fingerprintMaterial() {
        StringBuilder sb = new StringBuilder();
        for (ClarificationQuestionKind kind : values()) {
            sb.append(kind.name()).append('=').append(kind.templateText()).append('\n');
        }
        return sb.toString();
    }
}
