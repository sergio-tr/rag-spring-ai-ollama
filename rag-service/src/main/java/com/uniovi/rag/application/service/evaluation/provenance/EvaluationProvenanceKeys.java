package com.uniovi.rag.application.service.evaluation.provenance;

/** JSON keys for evaluation run provenance (aggregatesJson, export v1, metrics payload). */
public final class EvaluationProvenanceKeys {

    /** Key under {@code evaluation_run.aggregates_json}. */
    public static final String AGGREGATES_KEY = "evaluationProvenance";

    public static final String CHAT_PROVIDER = "chatProvider";
    public static final String EMBEDDING_PROVIDER = "embeddingProvider";
    public static final String GIT_SHA = "gitSha";
    public static final String BUILD_ID = "buildId";
    public static final String ENVIRONMENT_LABEL = "environmentLabel";
    public static final String PROMPT_PROFILE_VERSION = "promptProfileVersion";
    public static final String EFFECTIVE_SYSTEM_PROMPT_SHA256 = "effectiveSystemPromptSha256";
    public static final String PROMPT_BUNDLE_VERSION = "promptBundleVersion";
    public static final String PROMPT_BUNDLE_SHA256 = "promptBundleSha256";
    public static final String PROMPT_BUNDLE_INCLUDED_GROUPS = "promptBundleIncludedGroups";
    public static final String PROMPT_BUNDLE_EXCLUDED_GROUPS = "promptBundleExcludedGroups";

    private EvaluationProvenanceKeys() {}
}
