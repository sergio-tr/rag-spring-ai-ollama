import type { AsyncTaskStatusDto } from "@/types/api";

/** Backend ErrorCode names surfaced on failed chat/lab jobs when present. */
const KNOWN_CHAT_JOB_FAILURE_CODES = [
  "CHAT_DOCUMENT_SCOPE_EMPTY",
  "CHAT_DOCUMENT_FILTER_INVALID",
  "KNOWLEDGE_SNAPSHOT_UNAVAILABLE",
  "LLM_UNAVAILABLE",
  "UNSUPPORTED_RUNTIME_CONFIGURATION",
  "INTERNAL_ERROR",
] as const;

export type KnownChatJobFailureCode = (typeof KNOWN_CHAT_JOB_FAILURE_CODES)[number];

export function isKnownChatJobFailureCode(code: string): code is KnownChatJobFailureCode {
  return (KNOWN_CHAT_JOB_FAILURE_CODES as readonly string[]).includes(code);
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
}): string {
  const code = resolveChatJobFailureCode(options.task);
  if (code && isKnownChatJobFailureCode(code)) {
    return options.t(`chatJobFailure_${code}`);
  }
  const trimmed = options.errorMessageSanitized.trim();
  if (trimmed.length > 0) {
    return trimmed;
  }
  return options.t("assistantJobFailedShort");
}
