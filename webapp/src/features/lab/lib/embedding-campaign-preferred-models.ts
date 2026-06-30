/**
 * @deprecated Phase 5 — use `GET /lab/evaluation-models` and `compatibleWithCurrentVectorStore` from catalog API.
 * Retained for unit tests only; production Lab UI must not import this module.
 */
import { isLabComparisonAvailabilityBlocked } from "@/features/lab/lib/lab-comparison-availability";

export const EMBEDDING_CAMPAIGN_STORE_DIMENSION = 1024;

const EMBEDDING_CAMPAIGN_INCOMPATIBLE_PATTERN = /nomic-embed|qwen3-embedding/i;

export type EmbeddingModelAvailabilityStatus = "READY" | "BLOCKED_BY_MODEL_AVAILABILITY";

/** @deprecated */
export function isCampaignCompatibleEmbeddingModel(modelId: string): boolean {
  const trimmed = modelId.trim();
  return trimmed !== "" && !EMBEDDING_CAMPAIGN_INCOMPATIBLE_PATTERN.test(trimmed);
}

/** @deprecated */
export function filterCampaignCompatibleEmbeddingIds(modelIds: readonly string[]): string[] {
  return modelIds.filter(isCampaignCompatibleEmbeddingModel);
}

/** @deprecated */
export function embeddingComparisonAvailabilityStatus(
  compatibleAvailableCount: number,
  selectedCount = 0,
): EmbeddingModelAvailabilityStatus {
  return isLabComparisonAvailabilityBlocked(selectedCount, compatibleAvailableCount)
    ? "BLOCKED_BY_MODEL_AVAILABILITY"
    : "READY";
}

/** @deprecated */
export function isEmbeddingDimensionCompatible(
  probedDimension: number | null | undefined,
  expected: number = EMBEDDING_CAMPAIGN_STORE_DIMENSION,
): boolean {
  return probedDimension != null && probedDimension > 0 && probedDimension === expected;
}
