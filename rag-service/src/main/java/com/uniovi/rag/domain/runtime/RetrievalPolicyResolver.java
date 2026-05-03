package com.uniovi.rag.domain.runtime;

import com.uniovi.rag.configuration.RagFeatureConfiguration;

/**
 * Centralizes retrieval branch selection to avoid ad-hoc conditionals in the kernel.
 * Uses {@link RagEffectiveFeatures} so per-request {@link RagConfig} from {@link RagExecutionContextHolder} wins over the global bean.
 */
public final class RetrievalPolicyResolver {

    private RetrievalPolicyResolver() {
    }

    /**
     * Resolves the effective path for logging and metrics (after naive-corpus short-circuit is handled elsewhere).
     */
    public static RetrievalPath resolvePath(RagFeatureConfiguration global) {
        if (!RagEffectiveFeatures.useRetrieval(global)) {
            return RetrievalPath.NO_RETRIEVAL_LLM;
        }
        if (RagEffectiveFeatures.postRetrievalEnabled(global)) {
            return RetrievalPath.MANUAL_ONLY;
        }
        if (RagEffectiveFeatures.useAdvisor(global)) {
            return RetrievalPath.ADVISOR;
        }
        return RetrievalPath.MANUAL_ONLY;
    }

    /**
     * Whether the stock QuestionAnswerAdvisor fast path may run. Post-retrieval on forces manual path
     * unless {@code legacyAdvisorWithPostRetrieval} is true.
     */
    public static boolean allowQuestionAnswerAdvisor(
            RagFeatureConfiguration global,
            boolean advisorBeanPresent,
            boolean legacyAdvisorWithPostRetrieval) {
        if (!advisorBeanPresent) {
            return false;
        }
        if (!RagEffectiveFeatures.useAdvisor(global)) {
            return false;
        }
        if (RagEffectiveFeatures.postRetrievalEnabled(global)) {
            return legacyAdvisorWithPostRetrieval;
        }
        return true;
    }
}
