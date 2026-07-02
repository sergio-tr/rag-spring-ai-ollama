import type {
  CompatibleExperimentalPresetDto,
  CompatibleProductPresetDto,
  PresetCompatibilityDto,
  ProjectCompatiblePresetsDto,
} from "@/types/api";

export function isPresetCompatibilitySelectable(compatibility: PresetCompatibilityDto | null | undefined): boolean {
  return compatibility?.selectable === true;
}

export function presetCompatibilityDisabledReason(
  compatibility: PresetCompatibilityDto | null | undefined,
): string | null {
  if (!compatibility || compatibility.selectable) return null;
  return compatibility.disabledReason?.trim() || null;
}

export function filterCompatibleProductPresets(
  items: CompatibleProductPresetDto[] | undefined,
  showIncompatible: boolean,
): CompatibleProductPresetDto[] {
  const list = items ?? [];
  if (showIncompatible) return list;
  return list.filter((item) => isPresetCompatibilitySelectable(item.compatibility));
}

export function filterCompatibleExperimentalPresets(
  items: CompatibleExperimentalPresetDto[] | undefined,
  showIncompatible: boolean,
): CompatibleExperimentalPresetDto[] {
  const list = items ?? [];
  if (showIncompatible) return list;
  return list.filter((item) => isPresetCompatibilitySelectable(item.compatibility));
}

export function projectCompatiblePresetsEmptyState(
  catalog: ProjectCompatiblePresetsDto | null | undefined,
  showIncompatible: boolean,
): "no-compatible" | "no-index" | null {
  if (!catalog || showIncompatible) return null;
  const compatibleProduct = filterCompatibleProductPresets(catalog.productPresets, false);
  const compatibleExperimental = filterCompatibleExperimentalPresets(catalog.experimentalPresets, false);
  if (compatibleProduct.length > 0 || compatibleExperimental.length > 0) return null;
  if (!catalog.hasActiveIndex && catalog.readyDocumentCount === 0) {
    return "no-index";
  }
  return "no-compatible";
}

export function productPresetsFromCompatible(
  items: CompatibleProductPresetDto[] | undefined,
): CompatibleProductPresetDto["preset"][] {
  return (items ?? []).map((item) => item.preset);
}

export function experimentalPresetsFromCompatible(
  items: CompatibleExperimentalPresetDto[] | undefined,
): CompatibleExperimentalPresetDto["preset"][] {
  return (items ?? []).map((item) => item.preset);
}

export function compatibilityByProductPresetId(
  items: CompatibleProductPresetDto[] | undefined,
): Map<string, PresetCompatibilityDto> {
  const map = new Map<string, PresetCompatibilityDto>();
  for (const item of items ?? []) {
    map.set(item.preset.id, item.compatibility);
  }
  return map;
}

export function compatibilityByExperimentalPresetId(
  items: CompatibleExperimentalPresetDto[] | undefined,
): Map<string, PresetCompatibilityDto> {
  const map = new Map<string, PresetCompatibilityDto>();
  for (const item of items ?? []) {
    map.set(item.preset.productPresetId, item.compatibility);
  }
  return map;
}
