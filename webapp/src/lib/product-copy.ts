import { mapUserFacingErrorMessage, mapUserFacingErrorMessageEnglish } from "./user-facing-error-messages";
import {
  chatExperimentalPresetToDto,
  formatChatPresetSelectLabel,
  type ChatExperimentalPresetOptionInput,
} from "@/features/presets/lib/preset-display";
import {
  formatLatencyTierLabel,
  resolveExperimentalPresetLatencyTier,
} from "@/features/chat/lib/preset-latency-tier";

export type { ChatExperimentalPresetOptionInput };

/** Select option label for Chat configuration experimental presets (no P-code primary label). */
export function formatChatExperimentalPresetOptionLabel(
  p: ChatExperimentalPresetOptionInput,
  t: (key: string) => string,
): string {
  const base = formatChatPresetSelectLabel(chatExperimentalPresetToDto(p), t);
  const tier = formatLatencyTierLabel(resolveExperimentalPresetLatencyTier(p.code), t);
  const withTier = `${base} (${tier})`;
  if (p.chatSelectable && p.supported) {
    return withTier;
  }
  const hint = formatPresetSupportMessage(p.supportStatus, p.reasonIfUnsupported, t, "chatPresetNotSelectable");
  return `${withTier} (${hint})`;
}

const BENCHMARK_KIND_I18N: Record<string, string> = {
  LLM_JUDGE_QA: "benchmarkKindLabel.llm",
  EMBEDDING_RETRIEVAL: "benchmarkKindLabel.embedding",
  RAG_PRESET_END_TO_END: "benchmarkKindLabel.ragPreset",
  CLASSIFIER_METRICS: "benchmarkKindLabel.classifier",
};

const PRESET_SUPPORT_STATUS_I18N: Record<string, string> = {
  EXECUTABLE: "presetSupportStatus.executable",
  NOT_SUPPORTED: "presetSupportStatus.notSupported",
  REQUIRES_MULTI_TURN: "presetSupportStatus.requiresMultiTurn",
  FUTURE_MULTI_TURN_NOT_SELECTABLE: "presetSupportStatus.requiresMultiTurn",
};

/** Raw benchmark enum → Lab i18n key under `benchmarkKindLabel.*`. */
export function benchmarkKindI18nKey(kind: string | null | undefined): string {
  const normalized = (kind ?? "").trim().toUpperCase();
  return BENCHMARK_KIND_I18N[normalized] ?? "benchmarkKindLabel.unknown";
}

/** User-facing label for a backend benchmark kind enum. */
export function formatBenchmarkKindLabel(
  kind: string | null | undefined,
  t: (key: string) => string,
): string {
  const key = benchmarkKindI18nKey(kind);
  const out = t(key);
  return out === key ? t("benchmarkKindLabel.unknown") : out;
}

/** Maps preset catalog support status / reason codes to concise product copy. */
export function formatPresetSupportMessage(
  supportStatus: string | null | undefined,
  reasonIfUnsupported: string | null | undefined,
  t: (key: string) => string,
  fallbackKey = "labConfigUnsupportedPreset",
): string {
  const reason = reasonIfUnsupported?.trim();
  if (reason) {
    return mapUserFacingErrorMessage(reason, t, t(fallbackKey));
  }
  const status = supportStatus?.trim();
  if (status && PRESET_SUPPORT_STATUS_I18N[status]) {
    const i18nKey = PRESET_SUPPORT_STATUS_I18N[status];
    const out = t(i18nKey);
    if (out !== i18nKey && out.trim()) {
      return out;
    }
  }
  if (status && status !== "EXECUTABLE") {
    return mapUserFacingErrorMessage(status, t, t(fallbackKey));
  }
  return t(fallbackKey);
}

/** Patterns that must not appear in primary (non-collapsed) product UI copy. */
export function formatClassifierFallbackNote(
  raw: string | null | undefined,
  t: (key: string) => string,
): string {
  const trimmed = (raw ?? "").trim();
  if (!trimmed) {
    return "";
  }
  if (/^UNAVAILABLE/i.test(trimmed) || /^TIMEOUT/i.test(trimmed)) {
    return t("chatClassifierFallbackUnavailable");
  }
  if (trimmed === "INVALID_OUTPUT" || /^INVALID_OUTPUT/i.test(trimmed)) {
    return t("chatClassifierFallbackInvalidOutput");
  }
  if (/^INVALID_REQUEST/i.test(trimmed)) {
    return t("chatClassifierFallbackInvalidOutput");
  }
  const fb = t("chatClassifierFallbackUnavailable");
  return mapUserFacingErrorMessageEnglish(trimmed, fb);
}

/** Patterns that must not appear in primary (non-collapsed) product UI copy. */
export const FORBIDDEN_PRIMARY_UI_PATTERNS: RegExp[] = [
  /\bM9 experimental evidence\b/i,
  /\bM10\b|\bM11\b|\bM12\b|\bM9\b/i,
  /claim map/i,
  /docs/i,
  /handoff/i,
  /\bTFG\b/i,
  /Jaeger verified/i,
  /RAG ladder complete/i,
  /Do not claim/i,
  /\bRAG_PRESET_END_TO_END\b/,
  /\bLLM_JUDGE_QA\b/,
  /\bEMBEDDING_RETRIEVAL\b/,
  /\bFUTURE_MULTI_TURN_NOT_SELECTABLE\b/,
  /\bREQUIRES_MULTI_TURN\b/,
  /POST JSON/i,
  /Lab API -/i,
  /POST \/api/i,
  /\bcorpus\b/i,
  /Missing preferred/i,
  /missing preferred/i,
];

/** Drop or replace API-derived strings that must not appear in primary Lab UI surfaces. */
export function sanitizeLabPrimarySurfaceCopy(
  raw: string | null | undefined,
  fallback: string,
): string {
  const trimmed = (raw ?? "").trim();
  if (!trimmed) {
    return fallback;
  }
  for (const re of FORBIDDEN_PRIMARY_UI_PATTERNS) {
    if (re.test(trimmed)) {
      return fallback;
    }
  }
  return trimmed;
}
