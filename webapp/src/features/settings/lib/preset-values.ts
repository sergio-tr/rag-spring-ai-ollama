import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import {
  SANITIZED_RAG_CONFIG_KEY_SET,
  sanitizeRagConfigValues,
} from "@/features/settings/lib/rag-config-allowed-keys";
import { mergePayload } from "@/features/settings/lib/rag-config-values";

export function findKeysRejectedBySanitizer(source: Record<string, unknown>): string[] {
  const rejected: string[] = [];
  for (const k of Object.keys(source)) {
    if (!SANITIZED_RAG_CONFIG_KEY_SET.has(k)) rejected.push(k);
  }
  return rejected.sort((a, b) => a.localeCompare(b));
}

/** Split JSON import into structured-editable keys vs allowed-but-not-in-form extras (e.g. embeddingModel). */
export function partitionPresetImportValues(
  obj: Record<string, unknown>,
  editableKeys: string[],
): { structured: ConfigFormValues; extrasAllowed: Record<string, unknown> } {
  const editable = new Set(editableKeys);
  const structured: ConfigFormValues = {};
  const extrasAllowed: Record<string, unknown> = {};

  for (const [k, v] of Object.entries(obj)) {
    if (!SANITIZED_RAG_CONFIG_KEY_SET.has(k)) continue;
    if (editable.has(k)) {
      if (v !== undefined && v !== null) {
        structured[k] = v as string | number | boolean;
      }
    } else {
      extrasAllowed[k] = v;
    }
  }

  return { structured, extrasAllowed };
}

/** Final preset `values` map sent to POST — extras merged first; structured form wins; non-whitelisted keys dropped. */
export function buildPresetSaveValues(
  formValues: ConfigFormValues,
  editableKeys: string[],
  extrasAllowed: Record<string, unknown>,
): Record<string, unknown> {
  const merged = mergePayload(extrasAllowed, formValues, editableKeys);
  return sanitizeRagConfigValues(merged);
}
