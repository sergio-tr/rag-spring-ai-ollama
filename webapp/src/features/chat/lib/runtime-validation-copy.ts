import { sanitizePrimaryUiCopy } from "@/lib/forbidden-primary-ui-strings";
import type { RuntimeConfigValidationIssueDto } from "@/types/api";

const KNOWN_VALIDATION_I18N_CODES = ["TOOLS_FUNCTION_CALLING_PRECEDENCE"] as const;

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

/** Maps backend runtime validation issues to product-facing copy for normal UI surfaces. */
export function formatRuntimeValidationIssueMessage(
  issue: RuntimeConfigValidationIssueDto,
  t: (key: string) => string,
  fallbackKey = "chatValidationGeneric",
): string {
  const code = issue.code?.trim();
  if (code && isKnownValidationCode(code)) {
    const key = `chatValidation_${code}`;
    const mapped = t(key);
    if (mapped !== key && mapped.trim()) {
      return mapped;
    }
  }
  return sanitizePrimaryUiCopy(issue.message, t(fallbackKey));
}
