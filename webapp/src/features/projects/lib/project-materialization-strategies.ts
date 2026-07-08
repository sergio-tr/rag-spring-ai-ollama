export const NORMAL_PROJECT_MATERIALIZATION_STRATEGIES = [
  "CHUNK_LEVEL",
  "DOCUMENT_LEVEL",
  "HYBRID",
] as const;

export const ADVANCED_PROJECT_MATERIALIZATION_STRATEGIES = ["STRUCTURED_SEARCH"] as const;

export type NormalProjectMaterializationStrategy =
  (typeof NORMAL_PROJECT_MATERIALIZATION_STRATEGIES)[number];

export type AdvancedProjectMaterializationStrategy =
  (typeof ADVANCED_PROJECT_MATERIALIZATION_STRATEGIES)[number];

export type ProjectMaterializationStrategy =
  | NormalProjectMaterializationStrategy
  | AdvancedProjectMaterializationStrategy;

/** Opt-in via NEXT_PUBLIC_ENABLE_STRUCTURED_SEARCH_INDEXING=true at build time. */
export function isAdvancedStructuredSearchIndexingEnabled(): boolean {
  return process.env.NEXT_PUBLIC_ENABLE_STRUCTURED_SEARCH_INDEXING === "true";
}

export function listSelectableProjectMaterializationStrategies(): readonly ProjectMaterializationStrategy[] {
  if (isAdvancedStructuredSearchIndexingEnabled()) {
    return [...NORMAL_PROJECT_MATERIALIZATION_STRATEGIES, ...ADVANCED_PROJECT_MATERIALIZATION_STRATEGIES];
  }
  return NORMAL_PROJECT_MATERIALIZATION_STRATEGIES;
}

export function isStructuredSearchMaterializationStrategy(
  strategy: string | null | undefined,
): boolean {
  return (strategy ?? "").trim().toUpperCase() === "STRUCTURED_SEARCH";
}
