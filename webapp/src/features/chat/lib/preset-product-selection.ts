import type { RuntimeSnapshotCapabilitiesDto } from "@/types/api";
import { CHAT_DETERMINISTIC_DEFAULT_PRESET_ID } from "@/features/chat/lib/conversation-preset-ui";

/** Seeded Demo_Worst (direct LLM baseline). */
export const DEMO_WORST_PRESET_ID = "cafe0001-0001-4001-8001-000000000001";
/** Seeded Demo_NaiveFullCorpus. */
export const DEMO_NAIVE_PRESET_ID = "cafe0001-0001-4001-8001-000000000002";
export const DEMO_BEST_PRESET_ID = CHAT_DETERMINISTIC_DEFAULT_PRESET_ID;

export const P0_PRESET_ID = "cafe0001-0001-4001-8001-000000000010";
export const P1_PRESET_ID = "cafe0001-0001-4001-8001-000000000011";
export const P2_PRESET_ID = "cafe0001-0001-4001-8001-000000000012";
export const P3_PRESET_ID = "cafe0001-0001-4001-8001-000000000013";
export const P4_PRESET_ID = "cafe0001-0001-4001-8001-000000000014";
export const P5_PRESET_ID = "cafe0001-0001-4001-8001-000000000015";
export const P6_PRESET_ID = "cafe0001-0001-4001-8001-000000000016";
export const P7_PRESET_ID = "cafe0001-0001-4001-8001-000000000017";
export const P8_PRESET_ID = "cafe0001-0001-4001-8001-000000000018";

export const HYBRID_ADVANCED_PRESET_IDS = new Set([
  P8_PRESET_ID,
  "cafe0001-0001-4001-8001-000000000019", // P9
  "cafe0001-0001-4001-8001-000000000020", // P10
  "cafe0001-0001-4001-8001-000000000023", // P11
  "cafe0001-0001-4001-8001-000000000024", // P12
  "cafe0001-0001-4001-8001-000000000021", // P13
  "cafe0001-0001-4001-8001-000000000022", // P14
  "cafe0001-0001-4001-8001-000000000025", // P15
]);

/** P4–P7: metadata RAG, query intelligence, deterministic tools. */
export const CHUNK_METADATA_PRESET_IDS = new Set([
  P4_PRESET_ID,
  P5_PRESET_ID,
  P6_PRESET_ID,
  P7_PRESET_ID,
]);

export const METADATA_REQUIRED_PRESET_IDS = CHUNK_METADATA_PRESET_IDS;

/** All seeded catalog preset ids (P0–P15 + Demo_*). */
export const CATALOG_PRESET_IDS = [
  DEMO_WORST_PRESET_ID,
  DEMO_NAIVE_PRESET_ID,
  DEMO_BEST_PRESET_ID,
  P0_PRESET_ID,
  P1_PRESET_ID,
  P2_PRESET_ID,
  P3_PRESET_ID,
  P4_PRESET_ID,
  P5_PRESET_ID,
  P6_PRESET_ID,
  P7_PRESET_ID,
  ...HYBRID_ADVANCED_PRESET_IDS,
] as const;

export type PresetProductTier = "recommended" | "incompatible";

export type ProjectIndexCaps = Pick<
  RuntimeSnapshotCapabilitiesDto,
  "materializationStrategy" | "supportsMetadata"
>;

export type PresetIndexRequirements = {
  requiredMaterializationStrategy?: string | null;
  requiresMetadataSupport?: boolean;
} | null;

function normalizeMaterialization(raw: string | null | undefined): string {
  return (raw ?? "").trim().toUpperCase();
}

function isDirectLlmPreset(presetId: string): boolean {
  return presetId === P0_PRESET_ID || presetId === DEMO_WORST_PRESET_ID;
}

function isFullCorpusPreset(presetId: string): boolean {
  return presetId === P1_PRESET_ID || presetId === DEMO_NAIVE_PRESET_ID;
}

function isDirectOrFullCorpusPreset(presetId: string): boolean {
  return isDirectLlmPreset(presetId) || isFullCorpusPreset(presetId);
}

/** Presets that perform dense / document retrieval (blocked on STRUCTURED_SEARCH). */
export function isRetrievalPreset(presetId: string): boolean {
  return (
    presetId === P2_PRESET_ID ||
    presetId === P3_PRESET_ID ||
    CHUNK_METADATA_PRESET_IDS.has(presetId) ||
    HYBRID_ADVANCED_PRESET_IDS.has(presetId) ||
    presetId === DEMO_BEST_PRESET_ID
  );
}

/**
 * Presets that must never appear in the selector for the active index profile,
 * even when "show incompatible" is enabled.
 */
export function isPresetHardBlockedFromSelector(
  presetId: string,
  projectIndex: ProjectIndexCaps | null | undefined,
): boolean {
  const mat = normalizeMaterialization(projectIndex?.materializationStrategy);
  const meta = projectIndex?.supportsMetadata === true;

  if (mat === "STRUCTURED_SEARCH" && (isRetrievalPreset(presetId) || isFullCorpusPreset(presetId))) {
    return true;
  }
  if (!meta && METADATA_REQUIRED_PRESET_IDS.has(presetId)) {
    return true;
  }
  return false;
}

/**
 * Approved Phase 2.4 visibility: presets shown by default for the active index profile.
 */
function isRecommendedForIndexProfile(
  presetId: string,
  mat: string,
  meta: boolean,
): boolean {
  if (mat === "STRUCTURED_SEARCH") {
    return isDirectLlmPreset(presetId);
  }

  if (mat === "DOCUMENT_LEVEL") {
    return isDirectOrFullCorpusPreset(presetId) || presetId === P2_PRESET_ID;
  }

  if (mat === "CHUNK_LEVEL" && !meta) {
    return isDirectOrFullCorpusPreset(presetId) || presetId === P3_PRESET_ID;
  }

  if (mat === "CHUNK_LEVEL" && meta) {
    return (
      isDirectOrFullCorpusPreset(presetId) ||
      presetId === P3_PRESET_ID ||
      CHUNK_METADATA_PRESET_IDS.has(presetId)
    );
  }

  if (mat === "HYBRID" && !meta) {
    return isDirectOrFullCorpusPreset(presetId) || presetId === P3_PRESET_ID;
  }

  if (mat === "HYBRID" && meta) {
    if (presetId === DEMO_BEST_PRESET_ID || HYBRID_ADVANCED_PRESET_IDS.has(presetId)) {
      return true;
    }
    if (presetId === P3_PRESET_ID || CHUNK_METADATA_PRESET_IDS.has(presetId)) {
      return true;
    }
    if (isDirectLlmPreset(presetId)) {
      return true;
    }
    return false;
  }

  return isDirectOrFullCorpusPreset(presetId);
}

/**
 * Product-level preset tier for Chat / new conversation selection.
 */
export function classifyPresetProductTier(
  presetId: string,
  projectIndex: ProjectIndexCaps | null | undefined,
  selectable: boolean,
): PresetProductTier {
  if (!selectable) {
    return "incompatible";
  }
  const mat = normalizeMaterialization(projectIndex?.materializationStrategy);
  const meta = projectIndex?.supportsMetadata === true;
  return isRecommendedForIndexProfile(presetId, mat, meta) ? "recommended" : "incompatible";
}

export function isPresetVisibleInSelector(
  presetId: string,
  projectIndex: ProjectIndexCaps | null | undefined,
  selectable: boolean,
  showIncompatiblePresets: boolean,
): boolean {
  if (isPresetHardBlockedFromSelector(presetId, projectIndex)) {
    return false;
  }
  const tier = classifyPresetProductTier(presetId, projectIndex, selectable);
  if (tier === "recommended") {
    return true;
  }
  return showIncompatiblePresets;
}

export function listRecommendedSelectablePresetIds(
  presetEntries: Array<{ presetId: string; selectable: boolean }>,
  projectIndex: ProjectIndexCaps | null | undefined,
): string[] {
  return presetEntries
    .filter(({ presetId, selectable }) => classifyPresetProductTier(presetId, projectIndex, selectable) === "recommended")
    .map(({ presetId }) => presetId);
}

/** Returns preset ids visible in the default selector (no incompatible toggle). */
export function listDefaultVisiblePresetIds(
  presetIds: readonly string[],
  projectIndex: ProjectIndexCaps | null | undefined,
  selectable = true,
): string[] {
  return presetIds.filter((presetId) =>
    isPresetVisibleInSelector(presetId, projectIndex, selectable, false),
  );
}
