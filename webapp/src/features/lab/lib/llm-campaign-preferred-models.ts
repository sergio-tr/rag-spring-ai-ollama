/** @deprecated Legacy preferred model ids removed — Lab defaults come from evaluation catalog API. */
import { isLabComparisonAvailabilityBlocked } from "@/features/lab/lib/lab-comparison-availability";

export const LLM_CAMPAIGN_PREFERRED_MODEL_IDS = [] as const;

export type LlmModelAvailabilityStatus = "READY" | "BLOCKED_BY_MODEL_AVAILABILITY";

export function missingPreferredLlmModels(
  availableModelIds: readonly string[],
  preferred: readonly string[] = LLM_CAMPAIGN_PREFERRED_MODEL_IDS,
): string[] {
  const available = new Set(availableModelIds.map((id) => id.trim()).filter(Boolean));
  return preferred.filter((id) => !available.has(id));
}

export function llmComparisonAvailabilityStatus(
  availableCount: number,
  selectedCount = 0,
): LlmModelAvailabilityStatus {
  return isLabComparisonAvailabilityBlocked(selectedCount, availableCount)
    ? "BLOCKED_BY_MODEL_AVAILABILITY"
    : "READY";
}
