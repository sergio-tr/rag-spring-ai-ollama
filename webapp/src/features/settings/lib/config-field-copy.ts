/** Human-readable labels for known RAG config schema keys. */
export function labelConfigField(fieldKey: string, t: (key: string) => string): string {
  switch (fieldKey) {
    case "topK":
      return t("projectConfigFieldTopK");
    case "similarityThreshold":
      return t("projectConfigFieldSimilarityThreshold");
    case "llmModel":
      return t("projectConfigFieldLlmModel");
    case "llmSystemPrompt":
      return t("instructionsSystemLabel");
    case "embeddingModel":
      return t("projectConfigFieldEmbeddingModel");
    case "llmTemperature":
    case "temperature":
      return t("projectConfigFieldTemperature");
    case "expansionEnabled":
      return t("projectConfigFieldExpansionEnabled");
    case "nerEnabled":
      return t("projectConfigFieldNerEnabled");
    case "toolsEnabled":
      return t("projectConfigFieldToolsEnabled");
    case "metadataEnabled":
      return t("projectConfigFieldMetadataEnabled");
    default:
      return fieldKey;
  }
}
