export type RetrievalOverrideMode = "preset" | "assistant_defaults" | "custom";

export type RetrievalDefaults = {
  topK: number;
  similarityThreshold: number;
};

export function toRetrievalDefaults(
  source?: { topK?: number | null; similarityThreshold?: number | null } | null,
): RetrievalDefaults | null {
  if (!source) {
    return null;
  }
  const topK = source.topK;
  const similarityThreshold = source.similarityThreshold;
  if (typeof topK !== "number" || !Number.isFinite(topK)) {
    return null;
  }
  if (typeof similarityThreshold !== "number" || !Number.isFinite(similarityThreshold)) {
    return null;
  }
  return { topK, similarityThreshold };
}

const RETRIEVAL_OVERRIDE_KEYS = ["topK", "similarityThreshold"] as const;

export function hasRetrievalOverrideKeys(runtimeOverride: Record<string, unknown>): boolean {
  return RETRIEVAL_OVERRIDE_KEYS.some((key) => key in runtimeOverride);
}

export function numbersEqual(a: unknown, b: unknown): boolean {
  if (typeof a !== "number" || typeof b !== "number" || !Number.isFinite(a) || !Number.isFinite(b)) {
    return false;
  }
  return a === b;
}

export function inferRetrievalOverrideMode(
  runtimeOverride: Record<string, unknown>,
  assistantDefaults?: RetrievalDefaults | null,
): RetrievalOverrideMode {
  if (!hasRetrievalOverrideKeys(runtimeOverride)) {
    return "preset";
  }
  if (
    assistantDefaults &&
    numbersEqual(runtimeOverride.topK, assistantDefaults.topK) &&
    numbersEqual(runtimeOverride.similarityThreshold, assistantDefaults.similarityThreshold)
  ) {
    return "assistant_defaults";
  }
  return "custom";
}

export function buildRuntimeOverrideForRetrievalMode(
  current: Record<string, unknown>,
  mode: RetrievalOverrideMode,
  assistantDefaults?: RetrievalDefaults | null,
  seedFromEffective?: RetrievalDefaults | null,
): Record<string, unknown> {
  const next = { ...current };
  if (mode === "preset") {
    for (const key of RETRIEVAL_OVERRIDE_KEYS) {
      delete next[key];
    }
    return next;
  }
  if (mode === "assistant_defaults" && assistantDefaults) {
    return {
      ...next,
      topK: assistantDefaults.topK,
      similarityThreshold: assistantDefaults.similarityThreshold,
    };
  }
  if (mode === "custom" && !hasRetrievalOverrideKeys(next) && seedFromEffective) {
    return {
      ...next,
      topK: seedFromEffective.topK,
      similarityThreshold: seedFromEffective.similarityThreshold,
    };
  }
  return next;
}

export function buildInitialRuntimeOverrideForNewConversation(
  useAssistantDefaults: boolean,
  assistantDefaults?: RetrievalDefaults | null,
): Record<string, unknown> | undefined {
  if (!useAssistantDefaults || !assistantDefaults) {
    return undefined;
  }
  return {
    topK: assistantDefaults.topK,
    similarityThreshold: assistantDefaults.similarityThreshold,
  };
}
