/** True when user selected a multi-model comparison the catalog cannot satisfy. Single-model runs are allowed. */
export function isLabComparisonAvailabilityBlocked(
  selectedCount: number,
  availableCount: number,
): boolean {
  return selectedCount >= 2 && availableCount > 0 && availableCount < selectedCount;
}
