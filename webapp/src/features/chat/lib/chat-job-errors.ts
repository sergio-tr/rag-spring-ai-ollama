import type { AsyncTaskStatusDto } from "@/types/api";

import type { LlmProviderKind } from "@/lib/user-facing-error-messages";

/** Backend ErrorCode names surfaced on failed chat/lab jobs when present. */
export const KNOWN_CHAT_JOB_FAILURE_CODES = [
  "CHAT_DOCUMENT_SCOPE_EMPTY",
  "CHAT_DOCUMENT_FILTER_INVALID",
  "KNOWLEDGE_SNAPSHOT_UNAVAILABLE",
  "NO_DOCUMENTS",
  "NO_READY_DOCUMENTS",
  "NO_COMPATIBLE_SNAPSHOT",
  "SNAPSHOT_INCOMPATIBLE",
  "REINDEX_REQUIRED",
  "REINDEX_IN_PROGRESS",
  "REINDEX_FAILED",
  "MODEL_UNAVAILABLE",
  "CLASSIFIER_UNAVAILABLE",
  "DATE_MISMATCH",
  "NO_EXACT_ACTA_DATE",
  "LLM_UNAVAILABLE",
  "UNSUPPORTED_RUNTIME_CONFIGURATION",
  "INTERNAL_ERROR",
] as const;

const CHAT_FAILURE_CODE_ALIASES: Record<string, KnownChatJobFailureCode> = {
  NO_ACTIVE_INDEX: "REINDEX_REQUIRED",
  KNOWLEDGE_INDEX_UNAVAILABLE: "REINDEX_REQUIRED",
  SNAPSHOT_VECTOR_ROWS_MISSING: "SNAPSHOT_INCOMPATIBLE",
};

export type KnownChatJobFailureCode = (typeof KNOWN_CHAT_JOB_FAILURE_CODES)[number];

export function isKnownChatJobFailureCode(code: string): code is KnownChatJobFailureCode {
  return (KNOWN_CHAT_JOB_FAILURE_CODES as readonly string[]).includes(code);
}

export function normalizeChatFailureCode(code: string | null | undefined): KnownChatJobFailureCode | null {
  const raw = code == null ? "" : String(code).trim();
  if (!raw) {
    return null;
  }
  if (isKnownChatJobFailureCode(raw)) {
    return raw;
  }
  return CHAT_FAILURE_CODE_ALIASES[raw] ?? null;
}

export function chatFailureHintForCode(
  code: string | null | undefined,
  t: (key: string) => string,
  provider?: LlmProviderKind | null,
): string | null {
  const normalized = normalizeChatFailureCode(code);
  if (!normalized) {
    return null;
  }
  if (normalized === "LLM_UNAVAILABLE") {
    const key =
      provider === "OPENAI_COMPATIBLE"
        ? "chatJobFailure_LLM_UNAVAILABLE_OPENAI"
        : provider === "OLLAMA_NATIVE"
          ? "chatJobFailure_LLM_UNAVAILABLE_OLLAMA"
          : "chatJobFailure_LLM_UNAVAILABLE";
    return t(key);
  }
  if (normalized === "MODEL_UNAVAILABLE") {
    const key =
      provider === "OPENAI_COMPATIBLE"
        ? "chatJobFailure_MODEL_UNAVAILABLE_OPENAI"
        : provider === "OLLAMA_NATIVE"
          ? "chatJobFailure_MODEL_UNAVAILABLE_OLLAMA"
          : "chatJobFailure_MODEL_UNAVAILABLE";
    return t(key);
  }
  return t(`chatJobFailure_${normalized}`);
}

/** Prefer top-level DTO field; older payloads may stash the code only inside {@link AsyncTaskStatusDto.result}. */
export function resolveChatJobFailureCode(task: AsyncTaskStatusDto): string | null {
  if (task.failureCode && String(task.failureCode).trim()) {
    return String(task.failureCode).trim();
  }
  const raw = task.result?.failureCode;
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

/**
 * User-visible hint for a terminal FAILED assistant job: mapped copy for known backend codes,
 * otherwise sanitized backend text, otherwise generic assistant failure string.
 */
export function resolveChatJobFailureUserHint(options: {
  task: AsyncTaskStatusDto;
  errorMessageSanitized: string;
  t: (key: string) => string;
  provider?: LlmProviderKind | null;
}): string {
  const code = resolveChatJobFailureCode(options.task);
  const hint = chatFailureHintForCode(code, options.t, options.provider);
  if (hint) {
    return hint;
  }
  const trimmed = options.errorMessageSanitized.trim();
  if (trimmed.length > 0) {
    return trimmed;
  }
  return options.t("assistantJobFailedShort");
}
