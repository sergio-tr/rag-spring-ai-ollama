import type { ExperimentalPresetCatalogItemDto } from "@/types/api";

/** Preset selectable for single-turn Lab RAG benchmark (P0–P12). */
export function isLabBenchmarkPresetSelectable(p: ExperimentalPresetCatalogItemDto): boolean {
  return p.supported && p.singleTurnBenchmarkSelectable && p.labSelectable;
}

export function filterLabBenchmarkSelectablePresets(
  presets: ExperimentalPresetCatalogItemDto[] | undefined,
): ExperimentalPresetCatalogItemDto[] {
  return (presets ?? []).filter(isLabBenchmarkPresetSelectable);
}

/** Core single-turn ladder P0–P12 (thesis protocol spine). */
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
