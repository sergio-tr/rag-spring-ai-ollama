import type { ExperimentalPresetCatalogItemDto, RagPresetDto } from "@/types/api";

/** Product-facing capability badges for the Settings preset catalog. */
export type PresetCatalogFeatureKey =
  | "directLlm"
  | "fullCorpus"
  | "retrieval"
  | "documentLevelRetrieval"
  | "chunkLevelRetrieval"
  | "metadataAwareRetrieval"
  | "queryExpansion"
  | "nerStructuredRewrite"
  | "deterministicTools"
  | "hybridRetrieval"
  | "reranking"
  | "postRetrievalProcessing"
  | "functionCalling"
  | "advisor"
  | "adaptiveRouting"
  | "judge"
  | "clarification"
  | "memory"
  | "sourceAttribution"
  | "safeAbstention"
  | "retrievalSourceSupport"
  | "safeOperationalOverrides";

export type PresetCatalogFeatureState = {
  key: PresetCatalogFeatureKey;
  enabled: boolean;
};

/** Stable display order for the feature matrix. */
export const PRESET_CATALOG_FEATURE_ORDER: readonly PresetCatalogFeatureKey[] = [
  "directLlm",
  "fullCorpus",
  "retrieval",
  "documentLevelRetrieval",
  "chunkLevelRetrieval",
  "metadataAwareRetrieval",
  "queryExpansion",
  "nerStructuredRewrite",
  "deterministicTools",
  "hybridRetrieval",
  "reranking",
  "postRetrievalProcessing",
  "functionCalling",
  "advisor",
  "adaptiveRouting",
  "judge",
  "clarification",
  "memory",
  "sourceAttribution",
  "safeAbstention",
  "retrievalSourceSupport",
  "safeOperationalOverrides",
];

function boolFlag(value: unknown): boolean {
  return value === true || value === "true";
}

function normalizeMaterialization(raw: unknown): string {
  return String(raw ?? "")
    .trim()
    .toUpperCase();
}

/** Resolve terminal runtime values from a product preset row or experimental catalog item. */
export function resolvePresetRuntimeValues(
  source:
    | { kind: "product"; preset: Pick<RagPresetDto, "values"> }
    | { kind: "experimental"; preset: ExperimentalPresetCatalogItemDto },
): Record<string, unknown> {
  if (source.kind === "product") {
    return source.preset.values ?? {};
  }

  const experimental = source.preset;
  const terminal = (experimental.effectiveTerminalRuntimeJson ?? "").trim();
  if (terminal) {
    try {
      const parsed = JSON.parse(terminal) as unknown;
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>;
      }
    } catch {
      // fall through to runtimeFeatureFlags
    }
  }

  const caps = experimental.mapsToRuntimeCapabilities;
  const flags = caps?.runtimeFeatureFlags;
  if (flags && typeof flags === "object" && !Array.isArray(flags)) {
    return flags as Record<string, unknown>;
  }

  return {};
}

export function derivePresetCatalogFeatures(values: Record<string, unknown>): PresetCatalogFeatureState[] {
  const useRetrieval = boolFlag(values.useRetrieval);
  const naiveFullCorpus = boolFlag(values.naiveFullCorpusInPromptEnabled);
  const materialization = normalizeMaterialization(values.materializationStrategy);

  const directLlm = !useRetrieval && !naiveFullCorpus;
  const fullCorpus = naiveFullCorpus;
  const retrieval = useRetrieval && !naiveFullCorpus;
  const documentLevelRetrieval = retrieval && materialization === "DOCUMENT_LEVEL";
  const chunkLevelRetrieval = retrieval && materialization === "CHUNK_LEVEL";
  const hybridRetrieval = retrieval && materialization === "HYBRID";
  const metadataAwareRetrieval = boolFlag(values.metadataEnabled) && retrieval;
  const queryExpansion = boolFlag(values.expansionEnabled);
  const nerStructuredRewrite = boolFlag(values.nerEnabled);
  const deterministicTools = boolFlag(values.deterministicToolRoutingEnabled);
  const reranking = boolFlag(values.rankerEnabled);
  const postRetrievalProcessing = boolFlag(values.postRetrievalEnabled);
  const functionCalling = boolFlag(values.functionCallingEnabled);
  const advisor = boolFlag(values.useAdvisor);
  const adaptiveRouting = boolFlag(values.adaptiveRoutingEnabled);
  const judge = boolFlag(values.judgeEnabled);
  const clarification = boolFlag(values.clarificationEnabled);
  const memory = boolFlag(values.memoryEnabled);
  const sourceAttribution = retrieval || naiveFullCorpus || boolFlag(values.corpusGroundedDirectWorkflow);
  const safeAbstention = retrieval || hybridRetrieval || metadataAwareRetrieval || judge;
  const retrievalSourceSupport = true;
  const safeOperationalOverrides = true;

  const byKey: Record<PresetCatalogFeatureKey, boolean> = {
    directLlm,
    fullCorpus,
    retrieval,
    documentLevelRetrieval,
    chunkLevelRetrieval,
    metadataAwareRetrieval,
    queryExpansion,
    nerStructuredRewrite,
    deterministicTools,
    hybridRetrieval,
    reranking,
    postRetrievalProcessing,
    functionCalling,
    advisor,
    adaptiveRouting,
    judge,
    clarification,
    memory,
    sourceAttribution,
    safeAbstention,
    retrievalSourceSupport,
    safeOperationalOverrides,
  };

  return PRESET_CATALOG_FEATURE_ORDER.map((key) => ({ key, enabled: byKey[key] }));
}

export function deriveProductPresetCatalogFeatures(
  preset: Pick<RagPresetDto, "values">,
): PresetCatalogFeatureState[] {
  return derivePresetCatalogFeatures(resolvePresetRuntimeValues({ kind: "product", preset }));
}

export function deriveExperimentalPresetCatalogFeatures(
  preset: ExperimentalPresetCatalogItemDto,
): PresetCatalogFeatureState[] {
  return derivePresetCatalogFeatures(resolvePresetRuntimeValues({ kind: "experimental", preset }));
}

export function enabledPresetCatalogFeatureKeys(features: PresetCatalogFeatureState[]): PresetCatalogFeatureKey[] {
  return features.filter((f) => f.enabled).map((f) => f.key);
}

export function formatPresetCatalogFeatureLabelKey(key: PresetCatalogFeatureKey): string {
  return `presetCatalogFeature.${key}`;
}

export type PresetCatalogCompatibility = {
  requiredMaterializationStrategy: string | null;
  requiresMetadataSupport: boolean;
};

export function resolveExperimentalCompatibility(
  preset: ExperimentalPresetCatalogItemDto,
): PresetCatalogCompatibility | null {
  const req = preset.indexRequirements;
  if (!req) return null;
  return {
    requiredMaterializationStrategy: req.requiredMaterializationStrategy ?? null,
    requiresMetadataSupport: req.requiresMetadataSupport === true,
  };
}

/** Product demo presets expose compatibility derived from runtime materialization + metadata flags. */
export function resolveProductPresetCompatibility(
  preset: Pick<RagPresetDto, "values">,
): PresetCatalogCompatibility | null {
  const values = preset.values ?? {};
  const mat = normalizeMaterialization(values.materializationStrategy);
  const useRetrieval = boolFlag(values.useRetrieval);
  const metadata = boolFlag(values.metadataEnabled);

  if (!useRetrieval && !boolFlag(values.naiveFullCorpusInPromptEnabled)) {
    return { requiredMaterializationStrategy: null, requiresMetadataSupport: false };
  }

  if (boolFlag(values.naiveFullCorpusInPromptEnabled) && !useRetrieval) {
    return { requiredMaterializationStrategy: null, requiresMetadataSupport: false };
  }

  return {
    requiredMaterializationStrategy: mat || (useRetrieval ? "CHUNK_LEVEL" : null),
    requiresMetadataSupport: metadata,
  };
}
