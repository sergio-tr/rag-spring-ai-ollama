package com.uniovi.rag.service.evaluation.baseline;

/**
 * Stable, English baseline prompts for lab model benchmarks (versioned via {@link #PROFILE_VERSION}).
 *
 * <p>These fragments are snapshotted on each run; production chat presets are not used without an explicit snapshot.
 */
public final class EvaluationBaselinePrompts {

    /** Bump when any fragment changes (exported JSON carries {@link com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot#profileVersion()}). */
    public static final String PROFILE_VERSION = "eval-baseline-v1";

    public static final String BASE_SYSTEM =
            """
            You are a careful assistant answering strictly from the user-provided context.
            If the context does not contain enough information, say you cannot answer from the context alone.""";

    public static final String PROJECT_SYSTEM =
            """
            Evaluation mode: respond concisely and factually. Prefer quoting short spans when citing facts.""";

    public static final String CHAT_SYSTEM = "";

    public static final String RETRIEVAL_QUESTION_TEMPLATE =
            """
            Use only the DOCUMENT CONTEXT blocks below.
            Question: {{question}}""";

    public static final String ANSWER_FORMATTING =
            """
            Respond in plain text. Do not invent citations beyond provided context.""";

    private EvaluationBaselinePrompts() {}
}
