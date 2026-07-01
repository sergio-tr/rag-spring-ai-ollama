import type { PromptCatalogGroup } from "@/features/settings/hooks/use-prompt-catalog";

export const PROMPT_OVERRIDES_KEY = "promptOverrides";

export function readPromptOverrides(values: Record<string, unknown> | undefined): Record<string, string> {
  if (!values) return {};
  const nested = values[PROMPT_OVERRIDES_KEY];
  if (!nested || typeof nested !== "object" || Array.isArray(nested)) return {};
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(nested as Record<string, unknown>)) {
    if (typeof v === "string") out[k] = v;
  }
  return out;
}

export function effectivePromptContent(
  group: PromptCatalogGroup,
  overrides: Record<string, string>,
): string {
  const custom = overrides[group.id];
  if (typeof custom === "string" && custom.trim()) return custom;
  return group.defaultContent;
}

export function mergePromptOverrides(
  base: Record<string, unknown>,
  overrides: Record<string, string>,
): Record<string, unknown> {
  const cleaned: Record<string, string> = {};
  for (const [k, v] of Object.entries(overrides)) {
    if (v.trim()) cleaned[k] = v;
  }
  const next = { ...base };
  if (Object.keys(cleaned).length === 0) {
    delete next[PROMPT_OVERRIDES_KEY];
  } else {
    next[PROMPT_OVERRIDES_KEY] = cleaned;
  }
  return next;
}
