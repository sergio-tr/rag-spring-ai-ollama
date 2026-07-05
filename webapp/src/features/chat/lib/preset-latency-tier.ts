import type { ExperimentalPresetCatalogItemDto, RagPresetDto } from "@/types/api";
import { toProductPresetDisplayName, productPresetDescription } from "@/lib/product-preset-labels";

export type PresetLatencyTier = "fast" | "standard" | "advanced" | "research";

const DEMO_BEST_PRESET_ID = "cafe0001-0001-4001-8001-000000000003";

const EXPERIMENTAL_TIER: Record<string, PresetLatencyTier> = {
  P0: "fast",
  P1: "fast",
  P2: "fast",
  P3: "standard",
  P4: "standard",
  P5: "standard",
  P6: "standard",
  P7: "standard",
  P8: "standard",
  P9: "standard",
  P10: "standard",
  P11: "advanced",
  P12: "research",
  P13: "advanced",
  P14: "research",
  P15: "advanced",
};

export function isDemoBestPresetId(presetId: string | null | undefined): boolean {
  return (presetId ?? "").trim() === DEMO_BEST_PRESET_ID;
}

export function resolveExperimentalPresetLatencyTier(code: string): PresetLatencyTier {
  const normalized = code.trim().toUpperCase();
  return EXPERIMENTAL_TIER[normalized] ?? "standard";
}

export function resolveProductPresetLatencyTier(preset: Pick<RagPresetDto, "id" | "name">): PresetLatencyTier {
  const name = (preset.name ?? "").trim().toLowerCase();
  if (name.includes("worst")) return "fast";
  if (isDemoBestPresetId(preset.id)) return "standard";
  return "standard";
}

export function isResearchLatencyPreset(
  experimental: ExperimentalPresetCatalogItemDto | undefined,
): boolean {
  if (experimental) {
    const tier = resolveExperimentalPresetLatencyTier(experimental.code);
    return tier === "research" || tier === "advanced";
  }
  return false;
}

export function formatLatencyTierLabel(tier: PresetLatencyTier, t: (key: string) => string): string {
  const key = `presetLatencyTier.${tier}`;
  const out = t(key);
  return out === key ? tier : out;
}

export function formatProductPresetOptionLabel(
  preset: RagPresetDto,
  t: (key: string) => string,
): string {
  const tier = formatLatencyTierLabel(resolveProductPresetLatencyTier(preset), t);
  const recommended = isDemoBestPresetId(preset.id) ? ` · ${t("presetRecommendedForDemo")}` : "";
  const display = toProductPresetDisplayName(preset.name);
  return `${display} (${tier})${recommended}`;
}

/** Tooltip / title text for product preset select options. */
export function formatProductPresetOptionTitle(
  preset: RagPresetDto,
  t: (key: string) => string,
): string | undefined {
  if (isDemoBestPresetId(preset.id)) {
    const fromI18n = t("presetDemoBestDescription");
    if (fromI18n !== "presetDemoBestDescription" && fromI18n.trim()) {
      return fromI18n.trim();
    }
    return productPresetDescription(preset.name);
  }
  const desc = productPresetDescription(preset.name, t);
  return desc.trim() || undefined;
}
