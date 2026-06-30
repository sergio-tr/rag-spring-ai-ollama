import { describe, expect, it } from "vitest";
import type { ExperimentalPresetCatalogItemDto } from "@/types/api";
import {
  PRESET_P15_DESCRIPTION,
  PRESET_P15_DISPLAY_NAME,
  formatChatPresetSelectLabel,
  formatChatPresetTechnicalTitle,
  formatLabExperimentalPresetLabel,
  resolvePresetDisplayName,
  sortPresetsByRank,
} from "./preset-display";

const labT = (key: string) => key;
const chatT = (key: string) => {
  const map: Record<string, string> = {
    "presetDisplay.P15": PRESET_P15_DISPLAY_NAME,
    "presetDisplay.P15Description": PRESET_P15_DESCRIPTION,
  };
  return map[key] ?? key;
};

function preset(
  overrides: Partial<ExperimentalPresetCatalogItemDto> & Pick<ExperimentalPresetCatalogItemDto, "code">,
): ExperimentalPresetCatalogItemDto {
  return {
    productPresetId: "id",
    family: "S2",
    label: overrides.label ?? "Chunk + metadata retrieval",
    description: overrides.description ?? "Short objective",
    requiredCapabilities: [],
    supported: true,
    supportStatus: "EXECUTABLE",
    reasonIfUnsupported: null,
    requiresMultiTurn: false,
    mapsToRuntimeCapabilities: {},
    allowedOutcomes: ["EXECUTED"],
    chatSelectable: true,
    labSelectable: true,
    labOnly: false,
    protocolStageIndex: overrides.protocolStageIndex,
    ...overrides,
  };
}

describe("preset-display", () => {
  it("chat preset selector does not show P-codes as primary label", () => {
    const p = preset({ code: "P4", label: "Chunk + metadata retrieval", protocolStageIndex: 4 });
    expect(formatChatPresetSelectLabel(p, chatT)).toBe("Chunk + metadata retrieval");
    expect(formatChatPresetSelectLabel(p, chatT)).not.toMatch(/^P4\b/);
    expect(formatChatPresetTechnicalTitle(p, chatT)).toContain("P4");
  });

  it("lab secondary label uses functional name only", () => {
    const p = preset({
      code: "P14",
      label: "P14",
      protocolStageIndex: 14,
      mapsToRuntimeCapabilities: { runtimeFeatureFlags: { memoryEnabled: true } },
    });
    expect(formatLabExperimentalPresetLabel(p, labT)).toBe("Conversation memory");
  });

  it("presets sorted by rank", () => {
    const sorted = sortPresetsByRank([
      preset({ code: "P10", protocolStageIndex: 10 }),
      preset({ code: "P2", protocolStageIndex: 2 }),
      preset({ code: "P15", protocolStageIndex: 15 }),
    ]);
    expect(sorted.map((p) => p.code)).toEqual(["P2", "P10", "P15"]);
  });

  it("P15 copy synchronized", () => {
    const p = preset({ code: "P15", label: "P15", protocolStageIndex: 15, description: "" });
    expect(resolvePresetDisplayName(p, chatT)).toBe(PRESET_P15_DISPLAY_NAME);
    expect(formatLabExperimentalPresetLabel(p, chatT)).toBe(PRESET_P15_DISPLAY_NAME);
    expect(formatChatPresetTechnicalTitle(p, chatT)).toContain(PRESET_P15_DESCRIPTION);
  });
});
