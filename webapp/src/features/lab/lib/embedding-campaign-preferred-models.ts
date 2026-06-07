/** Default physical width of {@code vector_store.embedding} in this deployment. */
export const EMBEDDING_CAMPAIGN_STORE_DIMENSION = 1024;

/**
 * Tags known to mismatch the default 1024-wide pgvector column on common Ollama builds.
 * (nomic-embed-text → 768; qwen3-embedding may vary — excluded from Lab embedding comparison.)
 */
const EMBEDDING_CAMPAIGN_INCOMPATIBLE_PATTERN = /nomic-embed|qwen3-embedding/i;

export type EmbeddingModelAvailabilityStatus = "READY" | "BLOCKED_BY_MODEL_AVAILABILITY";

export function isCampaignCompatibleEmbeddingModel(modelId: string): boolean {
  const trimmed = modelId.trim();
  return trimmed !== "" && !EMBEDDING_CAMPAIGN_INCOMPATIBLE_PATTERN.test(trimmed);
}

export function filterCampaignCompatibleEmbeddingIds(modelIds: readonly string[]): string[] {
  return modelIds.filter(isCampaignCompatibleEmbeddingModel);
}

export function embeddingComparisonAvailabilityStatus(
  compatibleAvailableCount: number,
): EmbeddingModelAvailabilityStatus {
  return compatibleAvailableCount >= 2 ? "READY" : "BLOCKED_BY_MODEL_AVAILABILITY";
}

export function isEmbeddingDimensionCompatible(
  probedDimension: number | null | undefined,
  expected: number = EMBEDDING_CAMPAIGN_STORE_DIMENSION,
): boolean {
  return probedDimension != null && probedDimension > 0 && probedDimension === expected;
}
