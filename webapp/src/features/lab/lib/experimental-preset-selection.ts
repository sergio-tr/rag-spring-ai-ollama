import type { ExperimentalPresetCatalogItemDto } from "@/types/api";
import { isExtensionPreset } from "@/features/lab/lib/lab-benchmark-labels";

/** Preset selectable for single-turn Lab RAG benchmark (P0–P12). */
export function isLabBenchmarkPresetSelectable(p: ExperimentalPresetCatalogItemDto): boolean {
  return p.supported && (p.singleTurnBenchmarkSelectable ?? false) && p.labSelectable;
}

export function filterLabBenchmarkSelectablePresets(
  presets: ExperimentalPresetCatalogItemDto[] | undefined,
): ExperimentalPresetCatalogItemDto[] {
  return (presets ?? []).filter(isLabBenchmarkPresetSelectable);
}

/** Core single-turn preset codes P0–P12 for Lab RAG benchmark selection. */
export function isCoreExperimentalPresetCode(code: string): boolean {
  return /^P(?:[0-9]|1[0-2])$/.test(code);
}

export function listCoreExperimentalPresetCodes(presets: ExperimentalPresetCatalogItemDto[]): string[] {
  return filterLabBenchmarkSelectablePresets(presets)
    .map((p) => p.code)
    .filter(isCoreExperimentalPresetCode);
}

export function findInvalidLabPresetSelections(
  selected: string[],
  catalog: ExperimentalPresetCatalogItemDto[] | undefined,
): string[] {
  const byCode = new Map((catalog ?? []).map((p) => [p.code, p]));
  return selected.filter((code) => {
    const item = byCode.get(code);
    if (!item) return true;
    return !isLabBenchmarkPresetSelectable(item);
  });
}

/**
 * Removes presets that cannot run in single-turn Lab benchmarks (P13/P14 and other non-selectable codes).
 * Static P13/P14 removal works before the catalog loads; full catalog pass runs when {@code catalogReady}.
 */
export function sanitizeLabBenchmarkDraftPresetCodes(
  selected: readonly string[],
  catalog: ExperimentalPresetCatalogItemDto[] | undefined,
  catalogReady: boolean,
): { selected: string[]; removed: string[] } {
  if (selected.length === 0) {
    return { selected: [], removed: [] };
  }

  const removed: string[] = [];
  const kept: string[] = [];
  const byCode = catalogReady && catalog ? new Map(catalog.map((p) => [p.code, p])) : null;

  for (const raw of selected) {
    const code = raw.trim();
    if (!code) continue;
    if (isExtensionPreset(code)) {
      removed.push(code);
      continue;
    }
    if (byCode) {
      const item = byCode.get(code);
      if (!item || !isLabBenchmarkPresetSelectable(item)) {
        removed.push(code);
        continue;
      }
    }
    kept.push(code);
  }

  return { selected: kept, removed };
}
