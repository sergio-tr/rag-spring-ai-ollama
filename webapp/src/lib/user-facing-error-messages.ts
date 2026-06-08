/**
 * Maps stable backend / internal error codes to human copy for normal UI surfaces.
 * Technical codes may still appear under collapsed technical-details sections.
 */

/** i18n keys under the Lab namespace (pass `t` from useTranslations("Lab")). */
export const LAB_USER_ERROR_I18N_KEYS: Record<string, string> = {
  BLOCKED_BY_MODEL_AVAILABILITY: "userError_BLOCKED_BY_MODEL_AVAILABILITY",
  EMBEDDING_DIMENSION_MISMATCH: "userError_EMBEDDING_DIMENSION_MISMATCH",
  EMBEDDING_DIMENSION_UNAVAILABLE: "userError_EMBEDDING_DIMENSION_UNAVAILABLE",
  NO_READY_DOCUMENTS: "userError_NO_READY_DOCUMENTS",
  NO_DOCUMENTS: "userError_NO_DOCUMENTS",
  KB_EMPTY: "userError_NO_DOCUMENTS",
  KB_NOT_FOUND: "labKbNotFound",
  LAB_KB_LOAD_FAILED: "labKbLoadFailed",
  LAB_KB_CREATE_FAILED: "labKbCreateFailed",
  CORPUS_UNAVAILABLE: "labKbNotFound",
  STALE_CORPUS_SELECTION: "labCorpusStale",
  CORPUS_STALE: "labCorpusStale",
  NO_ACTIVE_SNAPSHOT: "labEvalIndexWillPrepare",
  REINDEX_REQUIRED: "labEvalIndexWillPrepare",
  INDEX_PREPARATION_REQUIRED: "labEvalIndexWillPrepare",
  NO_COMPATIBLE_SNAPSHOT: "userError_NO_COMPATIBLE_SNAPSHOT",
  SNAPSHOT_VECTOR_ROWS_MISSING: "userError_SNAPSHOT_INCOMPATIBLE",
  SNAPSHOT_INCOMPATIBLE: "userError_SNAPSHOT_INCOMPATIBLE",
  DOCUMENT_IMPORT_NOT_FOUND: "labImportDocumentNotFound",
  DOCUMENT_SCOPE_NOT_SHARED: "labImportScopeNotShared",
  DOCUMENT_BINARY_MISSING: "labImportBinaryMissing",
  NO_CORPUS_SELECTED: "benchmarkNeedsCorpus",
  DOCUMENT_PROCESSING_FAILED: "userError_DOCUMENT_PROCESSING_FAILED",
  FAILED_STALE_INGESTION: "userError_FAILED_STALE_INGESTION",
  FAILED_TIMEOUT: "userError_FAILED_STALE_INGESTION",
  FAILED_EMBEDDING: "userError_FAILED_EMBEDDING",
  FAILED_PARSING: "userError_FAILED_PARSING",
  FAILED_INDEX: "userError_FAILED_INDEX",
  FAILED_GENERIC: "userError_FAILED_GENERIC",
  MODEL_UNAVAILABLE: "userError_MODEL_UNAVAILABLE",
  DATASET_INVALID: "userError_DATASET_INVALID",
  EXPERIMENTAL_DATASET_INVALID: "userError_DATASET_INVALID",
  DUPLICATE_FILE: "labKbDuplicateFile",
  REINDEX_FAILED: "labEvalIndexPrepareFailed",
  SNAPSHOT_PREPARATION_FAILED: "labEvalIndexPrepareFailed",
  FILE_TOO_LARGE: "labCorpusFileTooLarge",
  UNSUPPORTED_TYPE: "labCorpusUnsupportedType",
  EXPERIMENTAL_PRESET_CODES_EMPTY: "labConfigNoPresets",
  UNSUPPORTED_PRESET: "labConfigUnsupportedPreset",
  PRESET_NOT_LAB_SELECTABLE: "labConfigNotSingleTurn",
  PRESET_NOT_SINGLE_TURN_BENCHMARK: "labConfigNotSingleTurn",
  PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED: "labConfigP13",
  PRESET_CONVERSATIONAL_MEMORY_BENCHMARK_NOT_SUPPORTED: "labConfigP14",
  FUTURE_MULTI_TURN_NOT_SELECTABLE: "labConfigNotSingleTurn",
  REQUIRES_MULTI_TURN: "labConfigNotSingleTurn",
  NOT_SUPPORTED: "labConfigUnsupportedPreset",
  INCOMPATIBLE_FEATURES: "labConfigInvalidFeatures",
  INVALID_RUNTIME_CONFIG: "labConfigInvalidFeatures",
  INVALID_FEATURE_COMBINATION: "labConfigInvalidFeatures",
  FEATURE_REQUIRES_INDEX: "labConfigRequiresIndex",
  FEATURE_REQUIRES_SNAPSHOT: "labConfigRequiresSnapshot",
  FEATURE_REQUIRES_REINDEX: "userError_REINDEX_REQUIRED",
  SNAPSHOT_CONFIG_MISMATCH: "userError_NO_COMPATIBLE_SNAPSHOT",
  RUNTIME_FEATURE_NOT_IMPLEMENTED: "labConfigNotImplemented",
  RUNTIME_NOT_IMPLEMENTED: "labConfigNotImplemented",
  CONFIG_VALIDATION_ERROR: "labConfigValidationError",
  RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE: "userError_RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE",
  USE_ADVISOR_REQUIRES_RETRIEVAL: "labConfigAdvisorRetrieval",
  STRUCTURED_SEARCH_WITH_RETRIEVAL_NOT_SUPPORTED: "labConfigStructuredSearch",
};

/** English fallbacks when no `t` function is available (e.g. generic api-client helper). */
export const USER_FACING_ERROR_MESSAGES_EN: Record<string, string> = {
  BLOCKED_BY_MODEL_AVAILABILITY:
    "Select at least two available models installed on the server to run a comparison.",
  EMBEDDING_DIMENSION_MISMATCH:
    "This embedding model is not compatible with the current vector index.",
  EMBEDDING_DIMENSION_UNAVAILABLE:
    "Could not determine the embedding model dimensions for this deployment.",
  NO_READY_DOCUMENTS: "No documents are ready in the knowledge base yet.",
  NO_DOCUMENTS: "Add documents to run this evaluation.",
  KB_EMPTY: "Add documents to run this evaluation.",
  INDEX_PREPARATION_REQUIRED: "The system will prepare the required index before running.",
  DOCUMENT_PROCESSING_FAILED:
    "One or more documents failed to process. Check the list and re-upload if needed.",
  FAILED_STALE_INGESTION: "Ingestion took too long. You can retry.",
  FAILED_TIMEOUT: "Ingestion took too long. You can retry.",
  FAILED_EMBEDDING: "Embedding failed for this document.",
  FAILED_PARSING: "Could not parse this document.",
  FAILED_INDEX: "Indexing failed for this document.",
  FAILED_GENERIC: "Document ingestion failed.",
  MODEL_UNAVAILABLE: "The model is not available in Ollama.",
  DATASET_INVALID: "The dataset is not valid. Check the template.",
  EXPERIMENTAL_DATASET_INVALID: "The dataset is not valid. Check the template.",
};

const SUBSTRING_CODE_HINTS: Array<{ pattern: string; code: string }> = [
  { pattern: "EMBEDDING_DIMENSION_MISMATCH", code: "EMBEDDING_DIMENSION_MISMATCH" },
  { pattern: "EMBEDDING_DIMENSION_UNAVAILABLE", code: "EMBEDDING_DIMENSION_UNAVAILABLE" },
  { pattern: "BLOCKED_BY_MODEL_AVAILABILITY", code: "BLOCKED_BY_MODEL_AVAILABILITY" },
  { pattern: "FAILED_STALE_INGESTION", code: "FAILED_STALE_INGESTION" },
  { pattern: "EXPERIMENTAL_DATASET_INVALID", code: "EXPERIMENTAL_DATASET_INVALID" },
];

/** Stable internal codes use SCREAMING_SNAKE with at least one underscore. */
const TECHNICAL_CODE_RE = /^[A-Z][A-Z0-9]*(_[A-Z0-9]+)+$/;

export function extractTechnicalErrorCode(raw: string | null | undefined): string | null {
  const trimmed = (raw ?? "").trim();
  if (!trimmed) {
    return null;
  }
  const head = trimmed.split(/[:\s]/)[0]?.trim() ?? "";
  if (TECHNICAL_CODE_RE.test(head)) {
    return head;
  }
  for (const hint of SUBSTRING_CODE_HINTS) {
    if (trimmed.includes(hint.pattern)) {
      return hint.code;
    }
  }
  return null;
}

function looksLikeInfrastructureErrorMessage(trimmed: string): boolean {
  const lower = trimmed.toLowerCase();
  return (
    lower.includes("preparedstatementcallback") ||
    lower.includes("badsqlgrammarexception") ||
    lower.includes("bad sql grammar") ||
    lower.includes("psqlexception") ||
    lower.includes("nullpointerexception") ||
    lower.includes("operator does not exist")
  );
}

export function isTechnicalErrorMessage(raw: string | null | undefined): boolean {
  const trimmed = (raw ?? "").trim();
  if (!trimmed) {
    return false;
  }
  if (looksLikeInfrastructureErrorMessage(trimmed)) {
    return true;
  }
  const code = extractTechnicalErrorCode(trimmed);
  if (!code) {
    return false;
  }
  if (trimmed === code) {
    return true;
  }
  if (trimmed.startsWith(`${code}:`) || trimmed.startsWith(`${code} `)) {
    return true;
  }
  return false;
}

const KNOWN_ERROR_CODES = new Set([
  ...Object.keys(LAB_USER_ERROR_I18N_KEYS),
  ...Object.keys(USER_FACING_ERROR_MESSAGES_EN),
]);

function resolveLabI18nKey(code: string): string | null {
  if (LAB_USER_ERROR_I18N_KEYS[code]) {
    return LAB_USER_ERROR_I18N_KEYS[code];
  }
  if (KNOWN_ERROR_CODES.has(code)) {
    return `userError_${code}`;
  }
  return null;
}

function translateLabKey(t: (key: string) => string, key: string): string | null {
  const out = t(key);
  return out !== key ? out : null;
}

/**
 * Resolves a user-visible error string for Lab surfaces.
 */
export function mapUserFacingErrorMessage(
  raw: string | null | undefined,
  t: (key: string) => string,
  fallback: string,
): string {
  const trimmed = (raw ?? "").trim();
  if (!trimmed) {
    return fallback;
  }

  const code = extractTechnicalErrorCode(trimmed);
  if (code) {
    const i18nKey = resolveLabI18nKey(code);
    if (i18nKey) {
      const translated = translateLabKey(t, i18nKey);
      if (translated) {
        return translated;
      }
    }
  }

  if (isTechnicalErrorMessage(trimmed)) {
    return fallback;
  }

  return trimmed.includes("corpus") || /Missing preferred/i.test(trimmed) ? fallback : trimmed;
}

/**
 * English-only resolver for shared helpers without React i18n context.
 */
export function mapUserFacingErrorMessageEnglish(
  raw: string | null | undefined,
  fallback: string,
): string {
  const trimmed = (raw ?? "").trim();
  if (!trimmed) {
    return fallback;
  }
  const code = extractTechnicalErrorCode(trimmed);
  if (code && USER_FACING_ERROR_MESSAGES_EN[code]) {
    return USER_FACING_ERROR_MESSAGES_EN[code];
  }
  if (isTechnicalErrorMessage(trimmed)) {
    return fallback;
  }
  return trimmed;
}
