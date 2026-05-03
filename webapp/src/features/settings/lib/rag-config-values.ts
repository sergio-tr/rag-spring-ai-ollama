import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";

export function pickFormValues(
  config: Record<string, unknown> | undefined,
  keys: string[],
): ConfigFormValues {
  const o: ConfigFormValues = {};
  for (const k of keys) {
    const v = config?.[k];
    if (v === undefined || v === null) continue;
    o[k] = v as string | number | boolean;
  }
  return o;
}

/** Merge editable form values into an optional existing config map (values omitted → unchanged/deleted per Rag rules). */
export function mergePayload(
  base: Record<string, unknown> | undefined,
  values: ConfigFormValues,
  keys: string[],
): Record<string, unknown> {
  const next: Record<string, unknown> = base ? { ...base } : {};
  for (const k of keys) {
    const v = values[k];
    if (v === undefined) {
      continue;
    }
    if (typeof v === "string" && v.trim() === "") {
      delete next[k];
    } else {
      next[k] = v;
    }
  }
  return next;
}
