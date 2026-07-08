import type { ConfigSchemaField } from "@/features/settings/hooks/use-rag-config";

/** Preferred display order for structured settings (form before JSON). */
export const STRUCTURED_CONFIG_FIELD_ORDER = [
  "llmSystemPrompt",
  "llmModel",
  "embeddingModel",
  "llmTemperature",
  "embeddingEncodingFormat",
  "embeddingDimensions",
  "embeddingTimeoutSeconds",
  "topK",
  "similarityThreshold",
  "materializationStrategy",
  "embeddingBatchSize",
  "embeddingMaxInputChars",
  "embeddingNormalize",
  "expansionEnabled",
  "nerEnabled",
  "toolsEnabled",
  "metadataEnabled",
] as const;

const ORDER_INDEX = new Map(STRUCTURED_CONFIG_FIELD_ORDER.map((key, index) => [key, index]));

/** Keys shown in the structured form even when schema marks them non-editable (e.g. embeddingModel). */
const FORCE_STRUCTURED_KEYS = new Set<string>(["embeddingModel"]);

export const ASSISTANT_INSTRUCTION_FIELD_KEYS = new Set(["llmSystemPrompt"]);

/** Keys excluded from structured Assistant Configuration (/settings/user) - per A1 architecture. */
export const USER_ASSISTANT_CONFIGURATION_EXCLUDED_KEYS = new Set([
  "llmModel",
  "llmTemperature",
  "temperature",
  "materializationStrategy",
  "expansionEnabled",
  "nerEnabled",
  "toolsEnabled",
  "metadataEnabled",
  "classifierModelId",
]);

/** Index-bound keys excluded from project structured form - shown in index profile section instead. */
export const PROJECT_INDEX_BOUND_EXCLUDED_KEYS = new Set([
  "embeddingModel",
  "embeddingEncodingFormat",
  "embeddingDimensions",
  "embeddingTimeoutSeconds",
  "embeddingBatchSize",
  "embeddingMaxInputChars",
  "embeddingNormalize",
  "embeddingTruncate",
  "materializationStrategy",
  "llmModel",
  "llmTemperature",
  "temperature",
]);

/** RAG feature toggles excluded from project settings UI (Phase 2.1 — retrieval params only). */
export const PROJECT_RETRIEVAL_FEATURE_TOGGLE_EXCLUDED_KEYS = new Set([
  "expansionEnabled",
  "nerEnabled",
  "toolsEnabled",
  "metadataEnabled",
]);

export const RETRIEVAL_PARAMETER_FIELD_KEYS = new Set(["topK", "similarityThreshold"]);

export function structuredConfigFieldsForMode(
  fields: ConfigSchemaField[],
  mode: "user" | "project",
): ConfigSchemaField[] {
  const structured = structuredConfigFields(fields);
  if (mode === "user") {
    return structured.filter((f) => !USER_ASSISTANT_CONFIGURATION_EXCLUDED_KEYS.has(f.key));
  }
  return structured.filter(
    (f) =>
      !PROJECT_INDEX_BOUND_EXCLUDED_KEYS.has(f.key) &&
      !PROJECT_RETRIEVAL_FEATURE_TOGGLE_EXCLUDED_KEYS.has(f.key),
  );
}

export function partitionConfigFields(fields: ConfigSchemaField[]): {
  instructionFields: ConfigSchemaField[];
  behaviorFields: ConfigSchemaField[];
} {
  const instructionFields = fields.filter((f) => ASSISTANT_INSTRUCTION_FIELD_KEYS.has(f.key));
  const behaviorFields = fields.filter((f) => !ASSISTANT_INSTRUCTION_FIELD_KEYS.has(f.key));
  return { instructionFields, behaviorFields };
}

export function structuredConfigFields(fields: ConfigSchemaField[]): ConfigSchemaField[] {
  const byKey = new Map(fields.map((field) => [field.key, field]));
  const ordered: ConfigSchemaField[] = [];

  for (const key of STRUCTURED_CONFIG_FIELD_ORDER) {
    const field = byKey.get(key);
    if (!field) continue;
    if (!field.userEditable && !FORCE_STRUCTURED_KEYS.has(key)) continue;
    ordered.push(field.userEditable ? field : { ...field, userEditable: true });
  }

  for (const field of fields) {
    if (!field.userEditable && !FORCE_STRUCTURED_KEYS.has(field.key)) continue;
    if (ordered.some((existing) => existing.key === field.key)) continue;
    ordered.push(field);
  }

  ordered.sort((a, b) => {
    const ai = ORDER_INDEX.get(a.key as (typeof STRUCTURED_CONFIG_FIELD_ORDER)[number]) ?? 999;
    const bi = ORDER_INDEX.get(b.key as (typeof STRUCTURED_CONFIG_FIELD_ORDER)[number]) ?? 999;
    if (ai !== bi) return ai - bi;
    return a.key.localeCompare(b.key);
  });

  return ordered;
}
