import { describe, expect, it } from "vitest";
import {
  DEMO_BEST_PRESET_ID,
  EXPERIMENTAL_DEFAULT_PRESET_PRIORITY,
  P3_PRESET_ID,
  resolveIndexAwareDefaultPreset,
} from "./resolve-index-aware-default-preset";
import type { ExperimentalPresetCatalogItemDto, ProjectCompatiblePresetsDto, RagPresetDto } from "@/types/api";

function mockPreset(id: string, system = false): RagPresetDto {
  return {
    id,
    name: id,
    description: null,
    tags: [],
    values: {},
    system,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
  };
}

function selectable(id: string, system = false) {
  return {
    preset: mockPreset(id, system),
    indexRequirements: null,
    compatibility: {
      selectable: true,
      disabledReasonCode: null,
      disabledReason: null,
      indexRequirements: null,
      compatibleWithActiveIndex: true,
    },
  };
}

function incompatible(id: string, reason: string) {
  return {
    preset: mockPreset(id, true),
    indexRequirements: null,
    compatibility: {
      selectable: false,
      disabledReasonCode: "MATERIALIZATION_NOT_SUPPORTED",
      disabledReason: reason,
      indexRequirements: null,
      compatibleWithActiveIndex: false,
    },
  };
}

function mockExperimentalPreset(
  id: string,
  selectableFlag: boolean,
): ExperimentalPresetCatalogItemDto {
  return {
    productPresetId: id,
    code: "P3",
    family: "S2",
    label: id,
    description: "Test preset",
    requiredCapabilities: [],
    supported: selectableFlag,
    supportStatus: selectableFlag ? "EXECUTABLE" : "NOT_SUPPORTED",
    reasonIfUnsupported: null,
    requiresMultiTurn: false,
    mapsToRuntimeCapabilities: {},
    allowedOutcomes: ["EXECUTED"],
    chatSelectable: selectableFlag,
    labSelectable: true,
    labOnly: false,
  };
}

function experimental(id: string, selectableFlag: boolean) {
  return {
    preset: mockExperimentalPreset(id, selectableFlag),
    compatibility: {
      selectable: selectableFlag,
      disabledReasonCode: selectableFlag ? null : "MATERIALIZATION_NOT_SUPPORTED",
      disabledReason: selectableFlag ? null : "Incompatible",
      indexRequirements: null,
      compatibleWithActiveIndex: selectableFlag,
    },
  };
}

function catalog(
  productPresets: ProjectCompatiblePresetsDto["productPresets"],
  experimentalPresets: ProjectCompatiblePresetsDto["experimentalPresets"] = [],
  activeSnapshotCapabilities: ProjectCompatiblePresetsDto["activeSnapshotCapabilities"] = {
    materializationStrategy: "CHUNK_LEVEL",
    supportsMetadata: false,
    embeddingModelId: "mxbai",
    chunkMaxChars: 400,
    chunkOverlap: 40,
  },
): ProjectCompatiblePresetsDto {
  return {
    projectId: "p1",
    effectiveEmbeddingModelId: "mxbai",
    hasActiveIndex: true,
    readyDocumentCount: 1,
    activeSnapshotCapabilities,
    productPresets,
    experimentalPresets,
  };
}

describe("resolveIndexAwareDefaultPreset", () => {
  it("prefers P3 when compatible on HYBRID+metadata index", () => {
    const result = resolveIndexAwareDefaultPreset(
      catalog(
        [selectable(DEMO_BEST_PRESET_ID, true), selectable(P3_PRESET_ID, true)],
        [],
        {
          materializationStrategy: "HYBRID",
          supportsMetadata: true,
          embeddingModelId: "mxbai",
          chunkMaxChars: 400,
          chunkOverlap: 40,
        },
      ),
    );
    expect(result.presetId).toBe(P3_PRESET_ID);
    expect(result.source).toBe("p3_default");
    expect(result.demoBestIncompatible).toBe(false);
  });

  it("falls back to chunk-recommended preset when Demo_Best is incompatible", () => {
    const result = resolveIndexAwareDefaultPreset(
      catalog([
        incompatible(DEMO_BEST_PRESET_ID, "Requires HYBRID"),
        selectable(P3_PRESET_ID, true),
      ]),
    );
    expect(result.presetId).toBe(P3_PRESET_ID);
    expect(result.source).toBe("p3_default");
    expect(result.demoBestIncompatible).toBe(true);
    expect(result.demoBestDisabledReason).toBe("Requires HYBRID");
  });

  it("falls back to P3 experimental preset on CHUNK_LEVEL-only projects", () => {
    const result = resolveIndexAwareDefaultPreset(
      catalog([incompatible(DEMO_BEST_PRESET_ID, "Requires HYBRID")], [experimental(P3_PRESET_ID, true)]),
    );
    expect(result.presetId).toBe(P3_PRESET_ID);
    expect(result.source).toBe("p3_default");
  });

  it("falls back to P0 when no retrieval-compatible preset exists", () => {
    const p0 = EXPERIMENTAL_DEFAULT_PRESET_PRIORITY[3];
    const result = resolveIndexAwareDefaultPreset(
      catalog([incompatible(DEMO_BEST_PRESET_ID, "Requires HYBRID")], [experimental(p0!, true)]),
    );
    expect(result.presetId).toBe(p0);
    expect(result.source).toBe("experimental");
  });

  it("returns null when catalog has no compatible presets", () => {
    const result = resolveIndexAwareDefaultPreset(
      catalog([incompatible(DEMO_BEST_PRESET_ID, "Requires HYBRID")]),
    );
    expect(result.presetId).toBeNull();
  });
});
