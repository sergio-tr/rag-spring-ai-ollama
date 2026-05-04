package com.uniovi.rag.service.evaluation.preset;

import com.uniovi.rag.configuration.RagFeatureConfiguration;

/** Deep-enough copy of {@link RagFeatureConfiguration} for evaluation overlays without mutating Spring beans. */
public final class RagFeatureConfigurationCopier {

    private RagFeatureConfigurationCopier() {}

    public static RagFeatureConfiguration copy(RagFeatureConfiguration src) {
        RagFeatureConfiguration c = new RagFeatureConfiguration();
        if (src == null) {
            return c;
        }
        c.setExpansionEnabled(src.isExpansionEnabled());
        c.setNerEnabled(src.isNerEnabled());
        c.setToolsEnabled(src.isToolsEnabled());
        c.setMetadataEnabled(src.isMetadataEnabled());
        c.setReasoningEnabled(src.isReasoningEnabled());
        c.setRankerEnabled(src.isRankerEnabled());
        c.setPostRetrievalEnabled(src.isPostRetrievalEnabled());
        c.setFunctionCallingEnabled(src.isFunctionCallingEnabled());
        c.setUseRetrieval(src.isUseRetrieval());
        c.setUseAdvisor(src.isUseAdvisor());
        c.setClarificationEnabled(src.isClarificationEnabled());
        c.setMemoryEnabled(src.isMemoryEnabled());
        c.setAdaptiveRoutingEnabled(src.isAdaptiveRoutingEnabled());
        c.setJudgeEnabled(src.isJudgeEnabled());
        return c;
    }
}
