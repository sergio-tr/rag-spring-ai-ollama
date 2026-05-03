/**
 * Human-readable labels for known project RAG config schema keys (GET /config/schema).
 * Unknown keys fall back to the raw key string.
 */
export function labelProjectConfigField(fieldKey: string, t: (key: string) => string): string {
  switch (fieldKey) {
    case "topK":
      return t("projectConfigFieldTopK");
    case "similarityThreshold":
      return t("projectConfigFieldSimilarityThreshold");
    case "llmModel":
      return t("projectConfigFieldLlmModel");
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
