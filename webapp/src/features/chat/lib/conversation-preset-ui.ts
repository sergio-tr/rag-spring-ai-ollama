import type { ConversationDto, RagPresetDto } from "@/types/api";

/**
 * Matches seeded `Demo_Worst` in backend migration `V18__demo_rag_presets.sql`
 * when `effectivePresetId` is absent (older API responses).
 */
export const CHAT_DETERMINISTIC_DEFAULT_PRESET_ID = "cafe0001-0001-4001-8001-000000000001";

/** UUID string always suitable for `<select value>` — never empty / "None". */
export function resolveConversationPresetSelectValue(conversation: ConversationDto | undefined): string {
  if (!conversation) return CHAT_DETERMINISTIC_DEFAULT_PRESET_ID;
  const persisted = conversation.presetId?.trim();
  if (persisted) return persisted;
  const effective = conversation.effectivePresetId?.trim();
  if (effective) return effective;
  return CHAT_DETERMINISTIC_DEFAULT_PRESET_ID;
}

export function findPresetById(
  presets: RagPresetDto[] | undefined,
  presetId: string,
): RagPresetDto | undefined {
  return presets?.find((p) => p.id === presetId);
}

/** User-visible label for the select; falls back when catalog has not loaded this id yet. */
export function resolvePresetSelectLabel(
  presets: RagPresetDto[] | undefined,
  selectValue: string,
  labels: { systemSuffix: string; serverDefault: string },
): string {
  const hit = findPresetById(presets, selectValue);
  if (hit) {
    return hit.system ? `${hit.name} (${labels.systemSuffix})` : hit.name;
  }
  if (selectValue === CHAT_DETERMINISTIC_DEFAULT_PRESET_ID) {
    return labels.serverDefault;
  }
  return labels.serverDefault;
}
