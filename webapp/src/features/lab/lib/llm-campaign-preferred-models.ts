/** Canonical LAB LLM comparison tags (Flyway V61 allowlist). */
import { isLabComparisonAvailabilityBlocked } from "@/features/lab/lib/lab-comparison-availability";

export const LLM_CAMPAIGN_PREFERRED_MODEL_IDS = [
  "llama3.1:8b",
  "gemma3:4b",
  "mistral:7b",
] as const;

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
