import type { ProjectCompatiblePresetsDto } from "@/types/api";
import {
  DEMO_BEST_PRESET_ID,
  listRecommendedSelectablePresetIds,
  P3_PRESET_ID,
  type ProjectIndexCaps,
} from "@/features/chat/lib/preset-product-selection";

export {
  DEMO_BEST_PRESET_ID,
  P3_PRESET_ID,
} from "@/features/chat/lib/preset-product-selection";

/** Experimental fallback order when P3 is incompatible (P8 → P4 → P2 → P0). */
export const EXPERIMENTAL_DEFAULT_PRESET_PRIORITY = [
  "cafe0001-0001-4001-8001-000000000018", // P8 HYBRID + metadata
  "cafe0001-0001-4001-8001-000000000014", // P4 CHUNK_LEVEL + metadata
  "cafe0001-0001-4001-8001-000000000012", // P2 DOCUMENT_LEVEL
  "cafe0001-0001-4001-8001-000000000010", // P0 direct LLM
] as const;

export type IndexAwareDefaultPresetSource = "p3_default" | "product" | "experimental";

export type IndexAwareDefaultPresetResult = {
  presetId: string | null;
  source: IndexAwareDefaultPresetSource | null;
  /** True when Demo_Best exists in catalog but is not selectable for this project index. */
  demoBestIncompatible: boolean;
  demoBestDisabledReason: string | null;
};

function isSelectableProduct(
  catalog: ProjectCompatiblePresetsDto,
  presetId: string,
): boolean {
  const item = catalog.productPresets.find((entry) => entry.preset.id === presetId);
  return item?.compatibility.selectable === true;
}

function isSelectableExperimental(
  catalog: ProjectCompatiblePresetsDto,
  presetId: string,
): boolean {
  const item = catalog.experimentalPresets.find((entry) => entry.preset.productPresetId === presetId);
  return item?.compatibility.selectable === true;
}

function recommendedPresetIds(
  catalog: ProjectCompatiblePresetsDto,
  projectIndex: ProjectIndexCaps | null | undefined,
): string[] {
  const entries = [
    ...catalog.productPresets.map((entry) => ({
      presetId: entry.preset.id,
      selectable: entry.compatibility.selectable,
    })),
    ...catalog.experimentalPresets.map((entry) => ({
      presetId: entry.preset.productPresetId,
      selectable: entry.compatibility.selectable,
    })),
  ];
  return listRecommendedSelectablePresetIds(entries, projectIndex);
}

/**
 * Picks the best recommended compatible preset for a new conversation on the active project.
 */
export function resolveIndexAwareDefaultPreset(
  catalog: ProjectCompatiblePresetsDto | null | undefined,
): IndexAwareDefaultPresetResult {
  if (!catalog) {
    return {
      presetId: null,
      source: null,
      demoBestIncompatible: false,
      demoBestDisabledReason: null,
    };
  }

  const projectIndex = catalog.activeSnapshotCapabilities;
  const demoBestEntry = catalog.productPresets.find((entry) => entry.preset.id === DEMO_BEST_PRESET_ID);
  const demoBestDisabledReason = demoBestEntry?.compatibility.disabledReason?.trim() || null;
  const recommended = recommendedPresetIds(catalog, projectIndex);

  const demoBestIncompatible = Boolean(demoBestEntry && demoBestEntry.compatibility.selectable !== true);

  if (recommended.includes(P3_PRESET_ID)) {
    if (isSelectableProduct(catalog, P3_PRESET_ID)) {
      return {
        presetId: P3_PRESET_ID,
        source: "p3_default",
        demoBestIncompatible,
        demoBestDisabledReason,
      };
    }
    if (isSelectableExperimental(catalog, P3_PRESET_ID)) {
      return {
        presetId: P3_PRESET_ID,
        source: "p3_default",
        demoBestIncompatible,
        demoBestDisabledReason,
      };
    }
  }

  for (const presetId of recommended) {
    if (presetId === P3_PRESET_ID || presetId === DEMO_BEST_PRESET_ID) continue;
    const product = catalog.productPresets.find((entry) => entry.preset.id === presetId);
    if (product?.compatibility.selectable) {
      return {
        presetId,
        source: "product",
        demoBestIncompatible,
        demoBestDisabledReason,
      };
    }
    if (isSelectableExperimental(catalog, presetId)) {
      return {
        presetId,
        source: "experimental",
        demoBestIncompatible,
        demoBestDisabledReason,
      };
    }
  }

  for (const presetId of EXPERIMENTAL_DEFAULT_PRESET_PRIORITY) {
    if (isSelectableExperimental(catalog, presetId)) {
      return {
        presetId,
        source: "experimental",
        demoBestIncompatible,
        demoBestDisabledReason,
      };
    }
  }

  return {
    presetId: null,
    source: null,
    demoBestIncompatible,
    demoBestDisabledReason,
  };
}
