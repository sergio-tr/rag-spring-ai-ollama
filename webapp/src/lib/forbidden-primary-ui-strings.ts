import { FORBIDDEN_PRODUCT_VISIBLE_PATTERNS } from "./forbidden-product-visible-patterns";

/**
 * Strings and patterns that must not appear in normal (non-collapsed) product UI.
 * Collapsed "Advanced technical details" sections may show diagnostic copy.
 */
export const FORBIDDEN_NORMAL_UI_STRING_PATTERNS: RegExp[] = [
  ...FORBIDDEN_PRODUCT_VISIBLE_PATTERNS.map((entry) => entry.pattern),
  /\bActive snapshot\b/i,
  /\bprompt bundle\b/i,
];

/** Bare "snapshot" as a user-facing label (not inside longer product phrases). */
export const FORBIDDEN_NORMAL_UI_SNAPSHOT_LABEL = /\bsnapshot\b/i;

export function containsForbiddenPrimaryUiString(text: string): boolean {
  const trimmed = text.trim();
  if (!trimmed) {
    return false;
  }
  for (const pattern of FORBIDDEN_NORMAL_UI_STRING_PATTERNS) {
    if (pattern.test(trimmed)) {
      return true;
    }
  }
  return false;
}

/** Replace API-derived copy that must not appear in primary surfaces. */
export function sanitizePrimaryUiCopy(raw: string | null | undefined, fallback: string): string {
  const trimmed = (raw ?? "").trim();
  if (!trimmed || containsForbiddenPrimaryUiString(trimmed)) {
    return fallback;
  }
  if (FORBIDDEN_NORMAL_UI_SNAPSHOT_LABEL.test(trimmed)) {
    return fallback;
  }
  return trimmed;
}
