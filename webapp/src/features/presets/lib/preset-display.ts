import type { ExperimentalPresetCatalogItemDto } from "@/types/api";

export type PresetDisplayModel = {
  internalCode: string;
  displayName: string;
  rank: number;
  description: string;
  family: string;
  isExperimental: boolean;
  isMemoryEnabled: boolean;
  isSelectableInChat: boolean;
  isSelectableInLab: boolean;
};

export type PresetCopyFn = (key: string) => string;

/** Canonical P15 copy aligned with `V64__experimental_preset_p15_integrated_single_turn.sql`. */
export const PRESET_P15_DISPLAY_NAME = "Integrated single-turn composition";
export const PRESET_P15_DESCRIPTION =
  "Hybrid retrieval, backend function calling, and adaptive route composition.";

const PRESET_BUILTIN_DISPLAY: Record<string, string> = {
  P14: "Memory-enabled preset",
  P15: PRESET_P15_DISPLAY_NAME,
};

const PRESET_BUILTIN_DESCRIPTION: Record<string, string> = {
  P15: PRESET_P15_DESCRIPTION,
};

export function presetRank(p: Pick<ExperimentalPresetCatalogItemDto, "code" | "protocolStageIndex">): number {
  if (typeof p.protocolStageIndex === "number" && Number.isFinite(p.protocolStageIndex)) {
    return p.protocolStageIndex;
  }
  const m = /^P(\d+)$/i.exec(p.code.trim());
  return m ? Number(m[1]) : 999;
}

/** Worst → best (ascending rank / protocol stage). */
export function sortPresetsByRank<T extends Pick<ExperimentalPresetCatalogItemDto, "code" | "protocolStageIndex">>(
  presets: readonly T[],
): T[] {
  return [...presets].sort((a, b) => presetRank(a) - presetRank(b));
}

function runtimeFlags(p: ExperimentalPresetCatalogItemDto): Record<string, unknown> {
  const raw = p.mapsToRuntimeCapabilities?.runtimeFeatureFlags;
  return raw && typeof raw === "object" ? (raw as Record<string, unknown>) : {};
}

export function isPresetMemoryEnabled(p: ExperimentalPresetCatalogItemDto): boolean {
  const flags = runtimeFlags(p);
  if (flags.memoryEnabled === true || flags.memoryEnabled === "true") {
    return true;
  }
  return p.code === "P14" || p.requiredCapabilities.some((c) => c.toUpperCase().includes("MEMORY"));
}

function isResolvedI18n(key: string, translated: string): boolean {
  if (!translated.trim()) return false;
  if (translated === key) return false;
  if (translated.includes(`Chat.${key}`) || translated.includes(`Lab.${key}`)) return false;
  return true;
}

export function resolvePresetDisplayName(
  p: ExperimentalPresetCatalogItemDto,
  t?: PresetCopyFn,
): string {
  if (t) {
    const i18nKey = `presetDisplay.${p.code}`;
    const translated = t(i18nKey);
    if (isResolvedI18n(i18nKey, translated)) {
      return translated.trim();
    }
  }

  const code = p.code.trim();
  const label = (p.label ?? "").trim();
  if (PRESET_BUILTIN_DISPLAY[code]) {
    return PRESET_BUILTIN_DISPLAY[code];
  }
  if (label && label !== code) {
    const stripped = label.replace(new RegExp(`^${code}\\s*[—–-]?\\s*`, "i"), "").trim();
    if (stripped && stripped !== code && !/^P\d+(_|$|\s)/i.test(stripped)) {
      return stripped;
    }
    if (!/^P\d+([_\s]|$)/i.test(label)) {
      return label;
    }
  }
  return label || code;
}

export function resolvePresetShortDescription(
  p: ExperimentalPresetCatalogItemDto,
  t?: PresetCopyFn,
): string {
  if (t) {
    const i18nKey = `presetDisplay.${p.code}Description`;
    const translated = t(i18nKey);
    if (isResolvedI18n(i18nKey, translated)) {
      return translated.trim();
    }
  }
  const builtin = PRESET_BUILTIN_DESCRIPTION[p.code];
  if (builtin) return builtin;
  const desc = (p.description ?? "").trim();
  if (!desc) return "";
  if (desc.length <= 96) return desc;
  return `${desc.slice(0, 93).trimEnd()}…`;
}

export function toPresetDisplayModel(
  p: ExperimentalPresetCatalogItemDto,
  t?: PresetCopyFn,
): PresetDisplayModel {
  return {
    internalCode: p.code,
    displayName: resolvePresetDisplayName(p, t),
    rank: presetRank(p),
    description: resolvePresetShortDescription(p, t) || (p.description ?? "").trim(),
    family: p.family,
    isExperimental: true,
    isMemoryEnabled: isPresetMemoryEnabled(p),
    isSelectableInChat: p.chatSelectable,
    isSelectableInLab: p.labSelectable,
  };
}

/** Chat select option — human display name only (no P-code). */
export function formatChatPresetSelectLabel(
  p: ExperimentalPresetCatalogItemDto,
  t?: PresetCopyFn,
): string {
  return resolvePresetDisplayName(p, t);
}

/** Tooltip / technical details — includes internal code and short description. */
export function formatChatPresetTechnicalTitle(
  p: ExperimentalPresetCatalogItemDto,
  t?: PresetCopyFn,
): string {
  const name = resolvePresetDisplayName(p, t);
  const desc = resolvePresetShortDescription(p, t);
  const parts = [`${p.code}`, name];
  if (desc) parts.push(desc);
  return parts.join(" · ");
}

/** Lab surfaces — researchers see protocol codes. */
export function formatLabExperimentalPresetLabel(
  p: ExperimentalPresetCatalogItemDto,
  t?: PresetCopyFn,
): string {
  return `${p.code} — ${resolvePresetDisplayName(p, t)}`;
}

export type ChatExperimentalPresetOptionInput = Readonly<{
  code: string;
  label: string;
  description?: string;
  supported: boolean;
  supportStatus: string | null;
  reasonIfUnsupported: string | null;
  requiresMultiTurn: boolean;
  chatSelectable: boolean;
  protocolStageIndex?: number;
  mapsToRuntimeCapabilities?: Record<string, unknown>;
  requiredCapabilities?: string[];
}>;

export function chatExperimentalPresetToDto(
  p: ChatExperimentalPresetOptionInput,
): ExperimentalPresetCatalogItemDto {
  return {
    productPresetId: "",
    code: p.code,
    family: "",
    label: p.label,
    description: p.description ?? "",
    requiredCapabilities: p.requiredCapabilities ?? [],
    supported: p.supported,
    supportStatus: (p.supportStatus ?? "EXECUTABLE") as ExperimentalPresetCatalogItemDto["supportStatus"],
    reasonIfUnsupported: p.reasonIfUnsupported,
    requiresMultiTurn: p.requiresMultiTurn,
    mapsToRuntimeCapabilities: p.mapsToRuntimeCapabilities ?? {},
    allowedOutcomes: ["EXECUTED"],
    chatSelectable: p.chatSelectable,
    labSelectable: false,
    labOnly: false,
    protocolStageIndex: p.protocolStageIndex,
  };
}
