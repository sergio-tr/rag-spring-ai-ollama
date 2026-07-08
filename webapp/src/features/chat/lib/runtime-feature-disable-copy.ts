import type { DisabledRuntimeFeatureDto, RuntimeConfigCapabilityDto } from "@/types/api";

/** Short inline tips beside disabled toggles (not long validation copy). */
const DISABLE_TIP_I18N_KEYS: Record<string, string> = {
  REQUIRES_useRetrieval: "chatFeatureTipRequiresRetrieval",
  REQUIRES_toolsEnabled: "chatFeatureTipRequiresTools",
  REQUIRES_tools: "chatFeatureTipRequiresTools",
  EXCLUDES_naiveFullCorpusInPromptEnabled: "chatFeatureTipIncompatibleFullContext",
  NOT_CONFIGURABLE_IN_CHAT: "chatFeatureTipNotAvailableInChat",
  STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED: "chatFeatureTipIndexNotSupported",
  STRUCTURED_SEARCH_FULL_CONTEXT_UNSUPPORTED: "chatFeatureTipRequiresVectorChunks",
  STRUCTURED_SEARCH_ADVANCED_RETRIEVAL_UNSUPPORTED: "chatFeatureTipRequiresRetrieval",
  NOT_IMPLEMENTED: "chatFeatureTipNotAvailableInChat",
  PRESET_BASE_FEATURE_LOCKED: "chatFeatureTipEnabledByPreset",
  PRESET_FEATURE_TOGGLE_DEFERRED: "chatFeatureTipPresetControlled",
  PROJECT_FEATURE_UNAVAILABLE: "chatFeatureTipIndexNotSupported",
};

function tipFromReasonCode(code: string | null | undefined, t: (key: string) => string): string | null {
  if (!code?.trim()) return null;
  const key = DISABLE_TIP_I18N_KEYS[code.trim()];
  if (!key) return null;
  const mapped = t(key);
  return mapped !== key && mapped.trim() ? mapped : null;
}

/** Maps backend disabledRuntimeFeatures entries to short toggle tips. */
export function formatDisabledRuntimeFeatureTip(
  item: DisabledRuntimeFeatureDto,
  t: (key: string) => string,
): string | null {
  return tipFromReasonCode(item.reasonCode, t);
}

function coerceBool(v: unknown): boolean {
  return v === true || v === "true";
}

/** Client-side short tips when runtime-state has not yet returned disabledRuntimeFeatures. */
export function clientSideDisableTip(
  key: string,
  cap: RuntimeConfigCapabilityDto | undefined,
  merged: Record<string, unknown>,
  t: (key: string) => string,
): string | null {
  if (key === "metadataEnabled" && coerceBool(merged.metadataEnabled) && !coerceBool(merged.toolsEnabled)) {
    return t("chatFeatureTipRequiresTools");
  }
  if (!cap) return null;
  if (!cap.configurableInChat) return null;
  if (!cap.implemented || !cap.engineWired) {
    return null;
  }
  for (const reqKey of cap.requires ?? []) {
    if (!coerceBool(merged[reqKey])) {
      if (reqKey === "useRetrieval") {
        return t("chatFeatureTipRequiresRetrieval");
      }
      if (reqKey === "toolsEnabled") {
        return t("chatFeatureTipRequiresTools");
      }
    }
  }
  for (const exKey of cap.excludes ?? []) {
    if (coerceBool(merged[exKey])) {
      if (exKey === "naiveFullCorpusInPromptEnabled") {
        return t("chatFeatureTipIncompatibleFullContext");
      }
    }
  }
  return null;
}
