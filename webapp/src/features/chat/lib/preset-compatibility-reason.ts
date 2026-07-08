import type { PresetCompatibilityDto } from "@/types/api";

const PRESET_COMPAT_I18N_BY_CODE: Record<string, string> = {
  MATERIALIZATION_NOT_SUPPORTED: "chatPresetRequiresCompatibleIndex",
  METADATA_SUPPORT_REQUIRED: "chatPresetCompatRequiresMetadata",
  NO_ACTIVE_INDEX: "chatPresetCompatNoActiveIndex",
  STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED: "chatPresetCompatStructuredSearchNoRetrieval",
};

const PRESET_COMPAT_I18N_BY_MESSAGE: Record<string, string> = {
  "Requires DOCUMENT_LEVEL index": "chatPresetCompatRequiresDocumentLevel",
  "Requires CHUNK_LEVEL index": "chatPresetCompatRequiresChunkLevel",
  "Requires HYBRID index": "chatPresetCompatRequiresHybrid",
  "Requires metadata-aware index capability": "chatPresetCompatRequiresMetadata",
  "Structured-search projects do not support retrieval-based RAG presets":
    "chatPresetCompatStructuredSearchNoRetrieval",
  "No active index": "chatPresetCompatNoActiveIndex",
};

export function formatPresetCompatibilityDisabledReason(
  compatibility: PresetCompatibilityDto | null | undefined,
  t: (key: string) => string,
): string | null {
  if (!compatibility || compatibility.selectable) return null;
  const raw = compatibility.disabledReason?.trim();
  const code = compatibility.disabledReasonCode?.trim();
  const normalizedRaw = raw?.replace(/\.$/, "");

  if (normalizedRaw && PRESET_COMPAT_I18N_BY_MESSAGE[normalizedRaw]) {
    const key = PRESET_COMPAT_I18N_BY_MESSAGE[normalizedRaw];
    const mapped = t(key);
    if (mapped !== key) return mapped;
  }

  if (raw) {
    if (raw.includes("DOCUMENT_LEVEL")) return t("chatPresetCompatRequiresDocumentLevel");
    if (raw.includes("CHUNK_LEVEL")) return t("chatPresetCompatRequiresChunkLevel");
    if (raw.includes("HYBRID")) return t("chatPresetCompatRequiresHybrid");
    if (raw.toLowerCase().includes("metadata")) return t("chatPresetCompatRequiresMetadata");
    if (raw.toLowerCase().includes("structured-search")) {
      return t("chatPresetCompatStructuredSearchNoRetrieval");
    }
    if (raw.toLowerCase().includes("no active index")) return t("chatPresetCompatNoActiveIndex");
    return raw;
  }

  if (code && PRESET_COMPAT_I18N_BY_CODE[code]) {
    const key = PRESET_COMPAT_I18N_BY_CODE[code];
    const mapped = t(key);
    if (mapped !== key) return mapped;
  }

  if (code) return code;
  return t("chatPresetRequiresCompatibleIndex");
}
