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
  SNAPSHOT_PREPARATION_FAILED: "labEvalIndexPrepareFailed",
  MATERIALIZATION_FAILED: "labEvalIndexPrepareFailed",
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
  LLM_UNAVAILABLE: "userError_INFERENCE_UNAVAILABLE",
  OLLAMA_UNAVAILABLE: "userError_INFERENCE_UNAVAILABLE",
  DATASET_INVALID: "userError_DATASET_INVALID",
  EXPERIMENTAL_DATASET_INVALID: "userError_DATASET_INVALID",
  DUPLICATE_FILE: "labKbDuplicateFile",
  REINDEX_FAILED: "labEvalIndexPrepareFailed",
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
  BLOCKED_BY_MODEL_AVAILABILITY_OPENAI:
    "Select at least two models configured in the API catalog to run a comparison.",
  EMBEDDING_DIMENSION_MISMATCH:
    "This embedding model is not compatible with the current vector index.",
  EMBEDDING_DIMENSION_UNAVAILABLE:
    "Could not determine the embedding model dimensions for this deployment.",
  NO_READY_DOCUMENTS: "No documents are ready in the knowledge base yet.",
  NO_DOCUMENTS: "Add documents to run this evaluation.",
  KB_EMPTY: "Add documents to run this evaluation.",
  INDEX_PREPARATION_REQUIRED: "The system will prepare the required index before running.",
  REINDEX_REQUIRED: "Index preparation is required before you can continue.",
  SNAPSHOT_INCOMPATIBLE: "The selected search index is incompatible with this configuration.",
  DOCUMENT_PROCESSING_FAILED:
    "One or more documents failed to process. Check the list and re-upload if needed.",
  FAILED_STALE_INGESTION: "Ingestion took too long. You can retry.",
  FAILED_TIMEOUT: "Ingestion took too long. You can retry.",
  FAILED_EMBEDDING: "Embedding failed for this document.",
  FAILED_PARSING: "Could not parse this document.",
  FAILED_INDEX: "Indexing failed for this document.",
  FAILED_GENERIC: "Document ingestion failed.",
  MODEL_UNAVAILABLE: "The selected model is unavailable. Choose another model or verify it is installed.",
  MODEL_UNAVAILABLE_OLLAMA: "The selected model is not available on the local model server. Install or verify it, then try again.",
  MODEL_UNAVAILABLE_OPENAI:
    "The selected model is unavailable on the configured API. Verify the model id or proxy settings.",
  INFERENCE_UNAVAILABLE:
    "The AI service is unavailable. Check your model provider and try again.",
  INFERENCE_UNAVAILABLE_OLLAMA:
    "The AI service is unavailable. Check that the local model server is running and models are installed, then try again.",
  INFERENCE_UNAVAILABLE_OPENAI:
    "The AI inference service is unavailable. Check that the configured LLM API is reachable, then try again.",
  VALIDATION_TOO_SHORT: "This value is too short.",
  VALIDATION_GENERIC: "Check this field and try again.",
  DATASET_INVALID: "The dataset is not valid. Check the template.",
  EXPERIMENTAL_DATASET_INVALID: "The dataset is not valid. Check the template.",
};

export type LlmProviderKind = "OLLAMA_NATIVE" | "OPENAI_COMPATIBLE";

export type UserFacingErrorDisplay = {
  primary: string;
  technical: string | null;
  action: string | null;
};

const SUBSTRING_CODE_HINTS: Array<{ pattern: string; code: string }> = [
  { pattern: "EMBEDDING_DIMENSION_MISMATCH", code: "EMBEDDING_DIMENSION_MISMATCH" },
  { pattern: "EMBEDDING_DIMENSION_UNAVAILABLE", code: "EMBEDDING_DIMENSION_UNAVAILABLE" },
  { pattern: "BLOCKED_BY_MODEL_AVAILABILITY", code: "BLOCKED_BY_MODEL_AVAILABILITY" },
  { pattern: "FAILED_STALE_INGESTION", code: "FAILED_STALE_INGESTION" },
  { pattern: "EXPERIMENTAL_DATASET_INVALID", code: "EXPERIMENTAL_DATASET_INVALID" },
  { pattern: "SNAPSHOT_PREPARATION_FAILED", code: "SNAPSHOT_PREPARATION_FAILED" },
  { pattern: "MATERIALIZATION_FAILED", code: "MATERIALIZATION_FAILED" },
  { pattern: "REINDEX_REQUIRED", code: "REINDEX_REQUIRED" },
  { pattern: "INDEX_PREPARATION_REQUIRED", code: "INDEX_PREPARATION_REQUIRED" },
];

const ZOD_VALIDATION_RE =
  /Too (?:small|big|short|long)|expected .+ to have|Invalid (?:enum|type|input|literal)|Required\b/i;
const INFERENCE_UNAVAILABLE_RE =
  /AI inference service is unavailable|AI service is unavailable|inference service is unavailable/i;
const OLLAMA_UNREACHABLE_RE = /ollama is (?:unreachable|not reachable|not running)/i;

/** Stable internal codes use SCREAMING_SNAKE with at least one underscore. */
const TECHNICAL_CODE_RE = /^[A-Z][A-Z0-9]*(_[A-Z0-9]+)+$/;

export function isZodLikeValidationMessage(raw: string | null | undefined): boolean {
  const trimmed = (raw ?? "").trim();
  if (!trimmed) {
    return false;
  }
  const head = trimmed.split(/[:\s]/)[0]?.trim() ?? "";
  if (TECHNICAL_CODE_RE.test(head)) {
    return false;
  }
  return ZOD_VALIDATION_RE.test(trimmed);
}

export function mentionsOllama(raw: string | null | undefined): boolean {
  return /ollama/i.test(raw ?? "");
}

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
  if (isZodLikeValidationMessage(trimmed)) {
    return true;
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

function providerSpecificKey(baseKey: string, provider?: LlmProviderKind | null): string {
  if (provider === "OPENAI_COMPATIBLE") {
    return `${baseKey}_OPENAI`;
  }
  if (provider === "OLLAMA_NATIVE") {
    return `${baseKey}_OLLAMA`;
  }
  return baseKey;
}

function resolveLabI18nKey(code: string, provider?: LlmProviderKind | null): string | null {
  if (code === "MODEL_UNAVAILABLE") {
    return providerSpecificKey("userError_MODEL_UNAVAILABLE", provider);
  }
  if (code === "LLM_UNAVAILABLE" || code === "OLLAMA_UNAVAILABLE") {
    return providerSpecificKey("userError_INFERENCE_UNAVAILABLE", provider);
  }
  if (code === "BLOCKED_BY_MODEL_AVAILABILITY") {
    return providerSpecificKey("userError_BLOCKED_BY_MODEL_AVAILABILITY", provider);
  }
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

function resolveValidationI18nKey(raw: string): string {
  if (/Too (?:small|short)/i.test(raw) || />=\s*1\s*character/i.test(raw)) {
    return "userError_VALIDATION_TOO_SHORT";
  }
  return "userError_VALIDATION_GENERIC";
}

function isInferenceUnavailableMessage(raw: string, code: string | null): boolean {
  return (
    INFERENCE_UNAVAILABLE_RE.test(raw) ||
    OLLAMA_UNREACHABLE_RE.test(raw) ||
    code === "LLM_UNAVAILABLE" ||
    code === "OLLAMA_UNAVAILABLE"
  );
}

function technicalPayload(raw: string, code: string | null): string {
  if (code && (raw === code || raw.startsWith(`${code}:`) || raw.startsWith(`${code} `))) {
    return code;
  }
  return raw;
}

function resolveActionForCode(code: string, t: (key: string) => string): string | null {
  if (
    code === "REINDEX_REQUIRED" ||
    code === "INDEX_PREPARATION_REQUIRED" ||
    code === "NO_ACTIVE_SNAPSHOT" ||
    code === "SNAPSHOT_PREPARATION_FAILED" ||
    code === "MATERIALIZATION_FAILED" ||
    code === "REINDEX_FAILED"
  ) {
    return translateLabKey(t, "userError_INDEX_ACTION");
  }
  if (code === "NO_READY_DOCUMENTS" || code === "DOCUMENT_PROCESSING_FAILED") {
    return translateLabKey(t, "userError_DOCUMENTS_ACTION");
  }
  return null;
}

function englishForCode(code: string, provider?: LlmProviderKind | null): string | null {
  if (code === "MODEL_UNAVAILABLE") {
    const key = providerSpecificKey("MODEL_UNAVAILABLE", provider);
    return USER_FACING_ERROR_MESSAGES_EN[key] ?? USER_FACING_ERROR_MESSAGES_EN.MODEL_UNAVAILABLE ?? null;
  }
  if (code === "LLM_UNAVAILABLE" || code === "OLLAMA_UNAVAILABLE") {
    const key = providerSpecificKey("INFERENCE_UNAVAILABLE", provider);
    return USER_FACING_ERROR_MESSAGES_EN[key] ?? USER_FACING_ERROR_MESSAGES_EN.INFERENCE_UNAVAILABLE ?? null;
  }
  if (code === "BLOCKED_BY_MODEL_AVAILABILITY") {
    const key = providerSpecificKey("BLOCKED_BY_MODEL_AVAILABILITY", provider);
    return USER_FACING_ERROR_MESSAGES_EN[key] ?? USER_FACING_ERROR_MESSAGES_EN.BLOCKED_BY_MODEL_AVAILABILITY ?? null;
  }
  return USER_FACING_ERROR_MESSAGES_EN[code] ?? null;
}

/**
 * Resolves primary copy, optional recommended action, and technical payload for details.
 */
export function resolveUserFacingErrorDisplay(options: {
  raw: string | null | undefined;
  t: (key: string) => string;
  fallback: string;
  provider?: LlmProviderKind | null;
  explicitCode?: string | null;
}): UserFacingErrorDisplay {
  const trimmed = (options.raw ?? "").trim();
  const fallback = options.fallback;
  if (!trimmed) {
    return { primary: fallback, technical: null, action: null };
  }

  const code = options.explicitCode?.trim() || extractTechnicalErrorCode(trimmed);

  if (isZodLikeValidationMessage(trimmed)) {
    const validationKey = resolveValidationI18nKey(trimmed);
    return {
      primary: translateLabKey(options.t, validationKey) ?? USER_FACING_ERROR_MESSAGES_EN.VALIDATION_GENERIC ?? fallback,
      technical: trimmed,
      action: translateLabKey(options.t, "userError_VALIDATION_ACTION"),
    };
  }

  if (isInferenceUnavailableMessage(trimmed, code)) {
    const i18nKey = resolveLabI18nKey(code ?? "LLM_UNAVAILABLE", options.provider);
    const primary =
      (i18nKey ? translateLabKey(options.t, i18nKey) : null) ??
      englishForCode("LLM_UNAVAILABLE", options.provider) ??
      fallback;
    return {
      primary,
      technical: technicalPayload(trimmed, code),
      action: translateLabKey(
        options.t,
        providerSpecificKey("userError_INFERENCE_ACTION", options.provider),
      ),
    };
  }

  if (code) {
    const i18nKey = resolveLabI18nKey(code, options.provider);
    if (i18nKey) {
      const translated = translateLabKey(options.t, i18nKey);
      if (translated) {
        return {
          primary: translated,
          technical: technicalPayload(trimmed, code),
          action: resolveActionForCode(code, options.t),
        };
      }
    }
    const english = englishForCode(code, options.provider);
    if (english) {
      return {
        primary: english,
        technical: technicalPayload(trimmed, code),
        action: resolveActionForCode(code, options.t),
      };
    }
    if (isTechnicalErrorMessage(trimmed)) {
      return { primary: fallback, technical: code, action: null };
    }
  }

  if (isTechnicalErrorMessage(trimmed)) {
    return { primary: fallback, technical: trimmed, action: null };
  }

  if (trimmed.includes("corpus") || /Missing preferred/i.test(trimmed)) {
    return { primary: fallback, technical: trimmed, action: null };
  }

  if (mentionsOllama(trimmed) && options.provider === "OPENAI_COMPATIBLE") {
    const i18nKey = resolveLabI18nKey("LLM_UNAVAILABLE", options.provider);
    const primary = (i18nKey ? translateLabKey(options.t, i18nKey) : null) ?? fallback;
    return { primary, technical: trimmed, action: translateLabKey(options.t, "userError_INFERENCE_ACTION_OPENAI") };
  }

  return {
    primary: trimmed,
    technical: code && code !== trimmed ? code : null,
    action: null,
  };
}

/**
 * Resolves a user-visible error string for Lab surfaces.
 */
export function mapUserFacingErrorMessage(
  raw: string | null | undefined,
  t: (key: string) => string,
  fallback: string,
  provider?: LlmProviderKind | null,
): string {
  return resolveUserFacingErrorDisplay({ raw, t, fallback, provider }).primary;
}

/**
 * English-only resolver for shared helpers without React i18n context.
 */
export function mapUserFacingErrorMessageEnglish(
  raw: string | null | undefined,
  fallback: string,
  provider?: LlmProviderKind | null,
): string {
  const echoT = (key: string) => {
    const code = key
      .replace(/^userError_/, "")
      .replace(/_OPENAI$/, "")
      .replace(/_OLLAMA$/, "");
    if (key.startsWith("userError_VALIDATION")) {
      if (key.includes("TOO_SHORT")) return USER_FACING_ERROR_MESSAGES_EN.VALIDATION_TOO_SHORT ?? fallback;
      return USER_FACING_ERROR_MESSAGES_EN.VALIDATION_GENERIC ?? fallback;
    }
    if (key.startsWith("userError_INFERENCE_UNAVAILABLE")) {
      return englishForCode("LLM_UNAVAILABLE", provider) ?? fallback;
    }
    if (key.startsWith("userError_MODEL_UNAVAILABLE")) {
      return englishForCode("MODEL_UNAVAILABLE", provider) ?? fallback;
    }
    if (key.startsWith("userError_BLOCKED_BY_MODEL_AVAILABILITY")) {
      return englishForCode("BLOCKED_BY_MODEL_AVAILABILITY", provider) ?? fallback;
    }
    return USER_FACING_ERROR_MESSAGES_EN[code] ?? fallback;
  };
  return resolveUserFacingErrorDisplay({ raw, t: echoT, fallback, provider }).primary;
}
