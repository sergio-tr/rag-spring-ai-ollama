import { extractTechnicalErrorCode } from "@/lib/user-facing-error-messages";

/** Product-safe skip/failure copy for benchmark result rows (terminal outcomes only). */
export function mapBenchmarkSkipReason(
  rawCodeOrMessage: string | null | undefined,
  t: (key: string) => string,
  fallback: string,
): { primary: string; technical: string } {
  const raw = (rawCodeOrMessage ?? "").trim();
  const code = extractTechnicalErrorCode(raw) ?? (raw && /^[A-Z][A-Z0-9_]+$/.test(raw) ? raw : null);
  const technical = raw || "-";

  if (!code) {
    if (raw.includes("prepare the required index") || raw.includes("will prepare")) {
      return { primary: t("benchmarkSkipIndexPreparationRequired"), technical };
    }
    if (raw.length > 0 && raw.length <= 120) {
      return { primary: raw, technical };
    }
    return { primary: fallback, technical };
  }

  const key = skipReasonI18nKey(code);
  if (key) {
    const translated = t(key);
    if (translated !== key) {
      return { primary: translated, technical: code };
    }
  }

  if (raw.includes("prepare the required index") || raw.includes("will prepare")) {
    return { primary: t("benchmarkSkipIndexPreparationRequired"), technical: code };
  }

  return { primary: fallback, technical: code };
}

function skipReasonI18nKey(code: string): string | null {
  switch (code) {
    case "REINDEX_REQUIRED":
    case "INDEX_PREPARATION_REQUIRED":
    case "NO_ACTIVE_SNAPSHOT":
    case "NO_ACTIVE_INDEX":
    case "FEATURE_REQUIRES_REINDEX":
    case "INDEX_REQUIRES_REINDEX":
      return "benchmarkSkipIndexPreparationRequired";
    case "REINDEX_FAILED":
    case "AUTO_REINDEX_FAILED":
    case "INDEX_BUILD_EMPTY":
    case "SNAPSHOT_INCOMPATIBLE":
    case "SNAPSHOT_VECTOR_ROWS_MISSING":
    case "NO_COMPATIBLE_SNAPSHOT":
    case "SNAPSHOT_CONFIG_MISMATCH":
      return "benchmarkSkipIndexPrepareFailed";
    case "NO_READY_DOCUMENTS":
    case "NO_DOCUMENTS":
    case "CORPUS_EMPTY":
    case "KB_EMPTY":
    case "NO_CORPUS_SELECTED":
    case "CORPUS_EVIDENCE_UNAVAILABLE":
      return "benchmarkSkipPresetCannotRunDocuments";
    case "MODEL_UNAVAILABLE":
      return "userError_MODEL_UNAVAILABLE";
    case "PRESET_NOT_SUPPORTED":
    case "NOT_SUPPORTED":
      return "labConfigUnsupportedPreset";
    default:
      return null;
  }
}
