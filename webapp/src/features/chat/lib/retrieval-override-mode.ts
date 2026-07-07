export type RetrievalOverrideMode = "preset" | "project_settings" | "assistant_defaults" | "custom";

export const RETRIEVAL_OVERRIDE_MODE_KEY = "retrievalOverrideMode";

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

export function readPersistedRetrievalOverrideMode(
  runtimeOverride: Record<string, unknown>,
): RetrievalOverrideMode | null {
  const raw = runtimeOverride[RETRIEVAL_OVERRIDE_MODE_KEY];
  if (
    raw === "preset" ||
    raw === "project_settings" ||
    raw === "assistant_defaults" ||
    raw === "custom"
  ) {
    return raw;
  }
  return null;
}

export function inferRetrievalOverrideMode(
  runtimeOverride: Record<string, unknown>,
  assistantDefaults?: RetrievalDefaults | null,
  projectDefaults?: RetrievalDefaults | null,
): RetrievalOverrideMode {
  const explicit = readPersistedRetrievalOverrideMode(runtimeOverride);
  if (explicit) {
    return explicit;
  }
  if (!hasRetrievalOverrideKeys(runtimeOverride)) {
    return "preset";
  }
  if (
    projectDefaults &&
    numbersEqual(runtimeOverride.topK, projectDefaults.topK) &&
    numbersEqual(runtimeOverride.similarityThreshold, projectDefaults.similarityThreshold)
  ) {
    return "project_settings";
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

function resolveCustomRetrievalValues(
  current: Record<string, unknown>,
  seedFromEffective?: RetrievalDefaults | null,
  assistantDefaults?: RetrievalDefaults | null,
): Pick<RetrievalDefaults, "topK" | "similarityThreshold"> {
  const seed = seedFromEffective ?? assistantDefaults;
  const topK =
    typeof current.topK === "number" && Number.isFinite(current.topK) ? current.topK : seed?.topK;
  const similarityThreshold =
    typeof current.similarityThreshold === "number" && Number.isFinite(current.similarityThreshold)
      ? current.similarityThreshold
      : seed?.similarityThreshold;
  return {
    topK: topK ?? 12,
    similarityThreshold: similarityThreshold ?? 0.25,
  };
}

/** Builds the PATCH payload for a retrieval source mode change (not a full snapshot). */
export function buildRetrievalModePatch(
  mode: RetrievalOverrideMode,
  current: Record<string, unknown>,
  seedFromEffective?: RetrievalDefaults | null,
  assistantDefaults?: RetrievalDefaults | null,
): Record<string, unknown> {
  if (mode === "preset") {
    return { [RETRIEVAL_OVERRIDE_MODE_KEY]: "preset" };
  }
  if (mode === "assistant_defaults") {
    return { [RETRIEVAL_OVERRIDE_MODE_KEY]: "assistant_defaults" };
  }
  if (mode === "project_settings") {
    return { [RETRIEVAL_OVERRIDE_MODE_KEY]: "project_settings" };
  }
  const values = resolveCustomRetrievalValues(current, seedFromEffective, assistantDefaults);
  return {
    [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom",
    topK: values.topK,
    similarityThreshold: values.similarityThreshold,
  };
}

/** @deprecated Prefer buildRetrievalModePatch for PATCH payloads. */
export function buildRuntimeOverrideForRetrievalMode(
  current: Record<string, unknown>,
  mode: RetrievalOverrideMode,
  assistantDefaults?: RetrievalDefaults | null,
  seedFromEffective?: RetrievalDefaults | null,
): Record<string, unknown> {
  const patch = buildRetrievalModePatch(mode, current, seedFromEffective, assistantDefaults);
  const next = { ...current, ...patch };
  if (mode === "preset") {
    delete next[RETRIEVAL_OVERRIDE_MODE_KEY];
    for (const key of RETRIEVAL_OVERRIDE_KEYS) {
      delete next[key];
    }
  } else if (mode === "assistant_defaults" || mode === "project_settings") {
    delete next.topK;
    delete next.similarityThreshold;
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
    [RETRIEVAL_OVERRIDE_MODE_KEY]: "assistant_defaults",
  };
}

/** Drops retrieval override keys when the active preset/runtime does not use retrieval. */
export function sanitizeRuntimeOverridePatch(
  patch: Record<string, unknown>,
  useRetrieval: boolean,
): Record<string, unknown> {
  if (useRetrieval) {
    return patch;
  }
  const next = { ...patch };
  delete next[RETRIEVAL_OVERRIDE_MODE_KEY];
  delete next.topK;
  delete next.similarityThreshold;
  return next;
}
