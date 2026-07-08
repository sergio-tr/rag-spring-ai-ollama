/** Runtime capability keys grouped for assistant configuration UI sections. */
export const RETRIEVAL_SETTING_KEYS = new Set([
  "useRetrieval",
  "naiveFullCorpusInPromptEnabled",
  "nerEnabled",
  "toolsEnabled",
  "metadataEnabled",
  "rankerEnabled",
  "postRetrievalEnabled",
  "useAdvisor",
]);

export const MEMORY_FEATURE_KEY = "memoryEnabled";
export const CLARIFICATION_FEATURE_KEY = "clarificationEnabled";
export const ANSWER_QUALITY_FEATURE_KEY = "judgeEnabled";

export const ADVANCED_RUNTIME_ONLY_KEYS = new Set([
  "functionCallingEnabled",
  "reasoningEnabled",
  "adaptiveRoutingEnabled",
]);

export function chatRuntimeLabelKey(capKey: string): string {
  const map: Record<string, string> = {
    useRetrieval: "runtimeFeatureUseRetrieval",
    memoryEnabled: "runtimeFeatureMemoryEnabled",
    clarificationEnabled: "runtimeFeatureClarificationEnabled",
    judgeEnabled: "runtimeFeatureAnswerQualityChecks",
    expansionEnabled: "runtimeFeatureExpansionEnabled",
    nerEnabled: "runtimeFeatureNerEnabled",
    toolsEnabled: "runtimeFeatureToolsEnabled",
    metadataEnabled: "runtimeFeatureMetadataEnabled",
    rankerEnabled: "runtimeFeatureRankerEnabled",
    postRetrievalEnabled: "runtimeFeaturePostRetrievalEnabled",
    useAdvisor: "runtimeFeatureUseAdvisor",
    functionCallingEnabled: "runtimeFeatureFunctionCallingEnabled",
    reasoningEnabled: "runtimeFeatureReasoningEnabled",
    adaptiveRoutingEnabled: "runtimeFeatureAdaptiveRoutingEnabled",
    naiveFullCorpusInPromptEnabled: "runtimeFeatureNaiveFullCorpus",
  };
  return map[capKey] ?? capKey;
}
