import { sanitizePrimaryUiCopy } from "@/lib/forbidden-primary-ui-strings";
import type { RuntimeConfigValidationIssueDto } from "@/types/api";

const KNOWN_VALIDATION_I18N_CODES = [
  "TOOLS_FUNCTION_CALLING_PRECEDENCE",
  "metadata_requires_tools",
  "STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED",
  "MUTUALLY_EXCLUSIVE",
  "REQUIRES_CAPABILITY",
  "USE_ADVISOR_REQUIRES_RETRIEVAL",
  "METADATA_SUPPORT_REQUIRED",
  "MATERIALIZATION_NOT_SUPPORTED",
  "NO_ACTIVE_INDEX",
  "UNSUPPORTED_RUNTIME_CONFIGURATION",
] as const;

/** Validation issues shown only inside collapsed Advanced technical details, not in normal chat config UI. */
export const ADVANCED_TECHNICAL_ONLY_VALIDATION_CODES = ["TOOLS_FUNCTION_CALLING_PRECEDENCE"] as const;

export function isAdvancedTechnicalValidationIssue(issue: RuntimeConfigValidationIssueDto): boolean {
  const code = issue.code?.trim();
  return Boolean(code && (ADVANCED_TECHNICAL_ONLY_VALIDATION_CODES as readonly string[]).includes(code));
}

type KnownValidationCode = (typeof KNOWN_VALIDATION_I18N_CODES)[number];

function isKnownValidationCode(code: string): code is KnownValidationCode {
  return (KNOWN_VALIDATION_I18N_CODES as readonly string[]).includes(code);
}

const INDEX_COMPATIBILITY_VALIDATION_CODES = new Set([
  "MATERIALIZATION_NOT_SUPPORTED",
  "METADATA_SUPPORT_REQUIRED",
  "NO_ACTIVE_INDEX",
  "STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED",
]);

function mapUnsupportedRuntimeConfigurationMessage(
  message: string | null | undefined,
  t: (key: string) => string,
): string | null {
  const raw = (message ?? "").trim();
  if (!raw) return null;
  if (/rankerEnabled requires useRetrieval/i.test(raw)) {
    return t("chatValidation_ranker_requires_retrieval");
  }
  if (/postRetrievalEnabled requires useRetrieval/i.test(raw)) {
    return t("chatValidation_post_retrieval_requires_retrieval");
  }
  if (/useAdvisor requires useRetrieval/i.test(raw)) {
    return t("chatValidation_advisor_requires_retrieval");
  }
  if (/requires useRetrieval/i.test(raw)) {
    return t("chatFeatureTipRequiresRetrieval");
  }
  return null;
}

function containsBackendFlagLeak(text: string): boolean {
  return (
    /\bunsupported-runtime-configuration\b/i.test(text) ||
    /\brankerEnabled\b/.test(text) ||
    /\bpostRetrievalEnabled\b/.test(text) ||
    /\buseAdvisor\b/.test(text) ||
    /\buseRetrieval\b/.test(text)
  );
}

/** Maps backend runtime validation issues to product-facing copy for normal UI surfaces. */
export function formatRuntimeValidationIssueMessage(
  issue: RuntimeConfigValidationIssueDto,
  t: (key: string) => string,
  fallbackKey = "chatValidationGeneric",
): string {
  const code = issue.code?.trim();
  if (code === "UNSUPPORTED_RUNTIME_CONFIGURATION") {
    const mapped = mapUnsupportedRuntimeConfigurationMessage(issue.message, t);
    if (mapped) return mapped;
  }
  if (code && isKnownValidationCode(code)) {
    const key = `chatValidation_${code}`;
    const mapped = t(key);
    if (mapped !== key && mapped.trim()) {
      return mapped;
    }
  }
  if (code && INDEX_COMPATIBILITY_VALIDATION_CODES.has(code) && issue.message?.trim()) {
    return sanitizePrimaryUiCopy(issue.message, t(fallbackKey));
  }
  if (code === "REQUIRES_CAPABILITY" && issue.message?.toLowerCase().includes("metadata")) {
    const mapped = t("chatValidation_metadata_requires_tools");
    if (mapped !== "chatValidation_metadata_requires_tools") return mapped;
  }
  const fromMessage = mapUnsupportedRuntimeConfigurationMessage(issue.message, t);
  if (fromMessage) return fromMessage;
  const sanitized = sanitizePrimaryUiCopy(issue.message, t(fallbackKey));
  if (containsBackendFlagLeak(sanitized)) {
    return t(fallbackKey);
  }
  return sanitized;
}

/** Raw diagnostic line for Advanced technical details only. */
export function formatAdvancedTechnicalValidationIssue(
  issue: RuntimeConfigValidationIssueDto,
): string {
  const code = issue.code?.trim() || "UNKNOWN";
  const message = issue.message?.trim();
  return message ? `${code}: ${message}` : code;
}
