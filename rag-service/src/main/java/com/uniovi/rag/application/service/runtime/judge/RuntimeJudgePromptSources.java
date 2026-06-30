package com.uniovi.rag.application.service.runtime.judge;

/** Frozen runtime judge prompt skeleton for {@link com.uniovi.rag.infrastructure.config.PromptBundleFingerprint}. */
public final class RuntimeJudgePromptSources {

    static final String TEMPLATE_RAW =
            """
            You are a post-answer judge for a RAG assistant.

            Question:
            %s

            Retrieved context (verify support only; do not invent facts beyond this):
            %s

            Candidate answer:
            %s

            Decide one label:
            - ACCEPTED
            - REJECTED_NO_RETRY
            - RETRY_REQUESTED

            Rules:
            - Output exactly one label on the first line.
            - %s
            - If the answer is acceptable and supported by the retrieved context, output ACCEPTED.
            - Reject incomplete participant lists, unsupported positive claims, and wrong dates.
            - Do not reject validated deterministic tool/metadata answers that directly answer the question.
            - Do not replace a correct answer with "no consta" or hedging disclaimers.
            - If the answer contains unsupported claims or is clearly incorrect, use REJECTED or RETRY; never hallucinate missing facts.
            - Prefer ACCEPTED when the answer is grounded in context, even if brief.

            Optionally include feedback after the first line starting with "FEEDBACK:".
            """;

    static final String RETRY_ALLOWED_LINE = "If the answer is not acceptable, output RETRY_REQUESTED.";
    static final String RETRY_DENIED_LINE = "If the answer is not acceptable, output REJECTED_NO_RETRY.";
    static final String EMPTY_CONTEXT_PLACEHOLDER = "(No retrieved context supplied.)";

    private RuntimeJudgePromptSources() {}

    public static String fingerprintMaterial() {
        return String.join(
                "\n---\n",
                TEMPLATE_RAW,
                RETRY_ALLOWED_LINE,
                RETRY_DENIED_LINE,
                EMPTY_CONTEXT_PLACEHOLDER);
    }
}
