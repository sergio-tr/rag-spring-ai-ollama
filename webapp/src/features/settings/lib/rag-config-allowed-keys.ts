/**
 * Keys accepted by the backend preset/config sanitizer (`RagConfigValueSanitizer`).
 * Keep aligned with `rag-service/.../RagConfigValueSanitizer.java` ALLOWED_KEYS.
 */
export const SANITIZED_RAG_CONFIG_KEYS = [
  "expansionEnabled",
  "nerEnabled",
  "toolsEnabled",
  "metadataEnabled",
  "reasoningEnabled",
  "rankerEnabled",
  "postRetrievalEnabled",
  "functionCallingEnabled",
  "useRetrieval",
  "useAdvisor",
  "topK",
  "similarityThreshold",
  "llmModel",
  "embeddingModel",
  "classifierModelId",
  "reasoningStrategy",
  "naiveFullCorpusInPromptEnabled",
  "llmSystemPrompt",
  "llmTemperature",
  "llmAdditionalParameters",
  "promptOverrides",
  "taskLlmOverrides",
] as const;

export type SanitizedRagConfigKey = (typeof SANITIZED_RAG_CONFIG_KEYS)[number];

export const SANITIZED_RAG_CONFIG_KEY_SET = new Set<string>(SANITIZED_RAG_CONFIG_KEYS);

export function sanitizeRagConfigValues(map: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(map)) {
    if (SANITIZED_RAG_CONFIG_KEY_SET.has(k)) {
      out[k] = v;
    }
  }
  return out;
}
