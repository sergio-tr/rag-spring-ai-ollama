package com.uniovi.rag.application.service.runtime.query;

/** Frozen query-rewrite prompts for {@link com.uniovi.rag.infrastructure.config.PromptBundleFingerprint}. */
public final class QueryRewritePromptSources {

    static final String SYSTEM_PROMPT =
            """
            You are a deterministic query rewriter.
            Return ONLY a JSON object. No markdown. No extra text.
            The response must start with { and end with }.
            """;

    static final String USER_PROMPT_TEMPLATE =
            """
            Rewrite the query in a constrained way.

            INPUTS:
            - normalizedText: "%s"
            - classifierStatus: "%s"
            - classifierLabel: "%s"
            - classifierQueryType: "%s"
            - extractedEntities:
              - dates: [%s]
              - people: [%s]
              - locations: [%s]
              - topics: [%s]
              - organizations: [%s]

            OUTPUT JSON SCHEMA (all keys required; use empty lists/maps when absent):
            {
              "rewrittenQueryText": "string",
              "targetEntities": ["string"],
              "targetAttributes": ["string"],
              "targetAction": "COUNT|LIST|FIND|EXPLAIN|SUMMARIZE|COMPARE|EXTRACT_FIELD|BOOLEAN_CHECK|UNKNOWN|null",
              "slotFilling": {"key":"value"},
              "constraints": ["string"]
            }

            CONSTRAINTS:
            - rewrittenQueryText MUST preserve any temporal constraints and must not drop named entities present in inputs
            - rewrittenQueryText MUST NOT introduce new named entities not present in inputs
            - rewrittenQueryText MUST NOT exceed 1.5x input length and MUST NOT exceed input length + 300
            - Do not invent missing constraints. If uncertain, leave fields empty and keep rewrittenQueryText close to input.
            """;

    private QueryRewritePromptSources() {}

    public static String fingerprintMaterial() {
        return SYSTEM_PROMPT + "\n---\n" + USER_PROMPT_TEMPLATE;
    }
}
