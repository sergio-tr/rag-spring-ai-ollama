import type { ConversationDto, ExperimentalPresetCatalogItemDto, RagPresetDto } from "@/types/api";
import { resolvePresetDisplayName } from "@/features/presets/lib/preset-display";
import { toProductPresetDisplayName } from "@/lib/product-preset-labels";

/**
 * Matches seeded `Demo_Best` in backend migration `V18__demo_rag_presets.sql`
 * when `effectivePresetId` is absent (older API responses).
 */
export const CHAT_DETERMINISTIC_DEFAULT_PRESET_ID = "cafe0001-0001-4001-8001-000000000003";

/** UUID string always suitable for `<select value>` — never empty / "None". */
export function resolveConversationPresetSelectValue(conversation: ConversationDto | undefined): string {
  if (!conversation) return CHAT_DETERMINISTIC_DEFAULT_PRESET_ID;
  const persisted = conversation.presetId?.trim();
  if (persisted) return persisted;
  const effective = conversation.effectivePresetId?.trim();
  if (effective) return effective;
  return CHAT_DETERMINISTIC_DEFAULT_PRESET_ID;
}

/**
 * Resolves `<select value>` for chat: conversation fields first, then catalog fallbacks when API omitted
 * effective id (stale rows), then configured default id. Avoids empty string / bogus "None" states.
 */
export function resolveChatPresetSelectValue(
  conversation: ConversationDto | undefined,
  presets: RagPresetDto[] | undefined,
): string {
  const persisted = conversation?.presetId?.trim();
  if (persisted) return persisted;
  const effective = conversation?.effectivePresetId?.trim();
  if (effective) return effective;

  const list = presets ?? [];
  if (list.length > 0) {
    const deterministic = list.find((p) => p.id === CHAT_DETERMINISTIC_DEFAULT_PRESET_ID);
    if (deterministic) return deterministic.id;
    const system = list.find((p) => p.system);
    if (system) return system.id;
    return list[0].id;
  }
  return CHAT_DETERMINISTIC_DEFAULT_PRESET_ID;
}

export function findPresetById(
  presets: RagPresetDto[] | undefined,
  presetId: string,
): RagPresetDto | undefined {
  return presets?.find((p) => p.id === presetId);
}

export type PresetSelectLabels = {
  systemSuffix: string;
  /** Configured default id present in schema but not in loaded catalog row yet. */
  recommendedDefault: string;
  /** Catalog successfully loaded with zero presets — controlled empty state. */
  defaultConfiguration: string;
};

/** User-visible label for the select; falls back when catalog has not loaded this id yet. */
export function resolvePresetSelectLabel(
  presets: RagPresetDto[] | undefined,
  selectValue: string,
  labels: PresetSelectLabels,
): string {
  const hit = findPresetById(presets, selectValue);
  if (hit) {
    const displayName = toProductPresetDisplayName(hit.name);
    return hit.system ? `${displayName} (${labels.systemSuffix})` : displayName;
  }
  const catalogLoadedEmpty = presets !== undefined && presets.length === 0;
  if (catalogLoadedEmpty && selectValue === CHAT_DETERMINISTIC_DEFAULT_PRESET_ID) {
    return labels.defaultConfiguration;
  }
  return labels.recommendedDefault;
}

/**
 * Chat preset label resolver across product + experimental catalogs.
 *
 * Rules:
 * - selectedPresetId null/empty -> Recommended Default
 * - product preset id -> product preset name (with system suffix when applicable)
 * - experimental preset id (matches `ExperimentalPresetCatalogItemDto.productPresetId`) -> human display name
 * - otherwise -> Unknown preset
 */
export function resolveChatPresetLabel(
  productPresets: RagPresetDto[] | undefined,
  experimentalPresets: ExperimentalPresetCatalogItemDto[] | undefined,
  selectedPresetId: string | null | undefined,
  labels: PresetSelectLabels,
): string {
  const id = (selectedPresetId ?? "").trim();
  if (!id) return labels.recommendedDefault;

  const p = productPresets?.find((x) => x.id === id);
  if (p) {
    const displayName = toProductPresetDisplayName(p.name);
    return p.system ? `${displayName} (${labels.systemSuffix})` : displayName;
  }

  const e = experimentalPresets?.find((x) => x.productPresetId === id);
  if (e) {
    return resolvePresetDisplayName(e);
  }

  const catalogLoadedEmpty =
    productPresets !== undefined &&
    experimentalPresets !== undefined &&
    productPresets.length === 0 &&
    experimentalPresets.length === 0;
  if (catalogLoadedEmpty && id === CHAT_DETERMINISTIC_DEFAULT_PRESET_ID) {
    return labels.defaultConfiguration;
  }

  return "Unknown preset";
}
