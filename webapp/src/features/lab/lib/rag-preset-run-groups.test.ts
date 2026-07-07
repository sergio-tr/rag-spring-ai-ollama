import { describe, expect, it } from "vitest";
import {
  buildRagPresetMaterializationGroups,
  presetRunGroupKeyFor,
} from "@/features/lab/lib/rag-preset-run-groups";
import type { ExperimentalPresetCatalogItemDto } from "@/types/api";

function preset(
  code: string,
  mat: string | null,
  metadata = false,
): ExperimentalPresetCatalogItemDto {
  const stage = Number.parseInt(code.replace(/^P/, ""), 10);
  return {
    productPresetId: code,
    code,
    family: "experimental",
    label: code,
    description: "",
    indexRequirements: {
      requiredMaterializationStrategy: mat,
      requiresMetadataSupport: metadata,
    },
    requiredCapabilities: [],
    supported: true,
    supportStatus: "EXECUTABLE",
    reasonIfUnsupported: null,
    requiresMultiTurn: false,
    mapsToRuntimeCapabilities: {},
    allowedOutcomes: ["EXECUTED"],
    chatSelectable: false,
    labSelectable: true,
    labOnly: true,
    singleTurnBenchmarkSelectable: true,
    protocolStageIndex: Number.isFinite(stage) ? stage : undefined,
  };
}

describe("rag-preset-run-groups", () => {
  it("groups P3 chunk separately from P8/P10 hybrid", () => {
    const catalog = [preset("P3", "CHUNK_LEVEL"), preset("P8", "HYBRID", true), preset("P10", "HYBRID", true)];
    const groups = buildRagPresetMaterializationGroups(["P3", "P8", "P10"], catalog);
    expect(groups.map((g) => g.groupKey)).toEqual(["CHUNK_LEVEL", "HYBRID_METADATA"]);
    expect(groups[0]?.presetCodes).toEqual(["P3"]);
    expect(groups[1]?.presetCodes).toEqual(["P8", "P10"]);
    expect(groups[1]?.materializationStrategy).toBe("HYBRID");
  });

  it("maps metadata chunk presets to CHUNK_LEVEL_METADATA", () => {
    expect(presetRunGroupKeyFor(preset("P5", "CHUNK_LEVEL", true))).toBe("CHUNK_LEVEL_METADATA");
  });
});
