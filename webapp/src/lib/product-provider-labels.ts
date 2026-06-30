/** Exact collapsed-section title per product language policy. */
export const ADVANCED_TECHNICAL_DETAILS_TITLE = "Advanced technical details";

export type ProductProviderLabels = {
  remoteApi: string;
  localServer: string;
};

/** Maps backend provider enums to product-facing labels (never show raw enum in UI). */
export function productProviderLabel(
  provider: string | undefined,
  labels: ProductProviderLabels,
): string | null {
  if (!provider) return null;
  if (provider === "OPENAI_COMPATIBLE") return labels.remoteApi;
  if (provider === "OLLAMA_NATIVE") return labels.localServer;
  return null;
}

export function productProviderLabelsFromSettings(
  t: (key: "configProviderOpenAiCompatible" | "configProviderOllamaNative") => string,
): ProductProviderLabels {
  return {
    remoteApi: t("configProviderOpenAiCompatible"),
    localServer: t("configProviderOllamaNative"),
  };
}

export function productProviderLabelsFromAdmin(
  t: (key: "catalogProviderRemoteApi" | "catalogProviderLocalServer") => string,
): ProductProviderLabels {
  return {
    remoteApi: t("catalogProviderRemoteApi"),
    localServer: t("catalogProviderLocalServer"),
  };
}
