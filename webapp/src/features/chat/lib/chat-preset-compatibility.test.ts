import { describe, expect, it } from "vitest";
import type { CompatibleProductPresetDto, ProjectCompatiblePresetsDto } from "@/types/api";
import {
  filterCompatibleExperimentalPresets,
  filterCompatibleProductPresets,
  projectCompatiblePresetsEmptyState,
} from "./chat-preset-compatibility";

function makeCompatibleProductPreset(
  id: string,
  name: string,
  selectable: boolean,
): CompatibleProductPresetDto {
  return {
    preset: {
      id,
      name,
      description: null,
      tags: [],
      values: {},
      system: false,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
    indexRequirements: null,
    compatibility: selectable
      ? {
          selectable: true,
          disabledReasonCode: null,
          disabledReason: null,
          indexRequirements: null,
          compatibleWithActiveIndex: true,
        }
      : {
          selectable: false,
          disabledReasonCode: "INDEX_INCOMPATIBLE",
          disabledReason: "Incompatible",
          indexRequirements: null,
          compatibleWithActiveIndex: false,
        },
  };
}

describe("chat-preset-compatibility", () => {
  const productItems = [
    makeCompatibleProductPreset("a", "A", true),
    makeCompatibleProductPreset("b", "B", false),
  ];

  it("filters incompatible product presets unless advanced mode is on", () => {
    expect(filterCompatibleProductPresets([...productItems], false)).toHaveLength(1);
    expect(filterCompatibleProductPresets([...productItems], true)).toHaveLength(2);
  });

  it("returns no-index empty state when project has no ready docs or active index", () => {
    const catalog: ProjectCompatiblePresetsDto = {
      projectId: "p1",
      effectiveEmbeddingModelId: null,
      hasActiveIndex: false,
      readyDocumentCount: 0,
      activeSnapshotCapabilities: null,
      productPresets: [productItems[1]],
      experimentalPresets: [],
    };
    expect(projectCompatiblePresetsEmptyState(catalog, false)).toBe("no-index");
  });

  it("returns no-compatible empty state when index exists but nothing matches", () => {
    const catalog: ProjectCompatiblePresetsDto = {
      projectId: "p1",
      effectiveEmbeddingModelId: "mxbai",
      hasActiveIndex: true,
      readyDocumentCount: 2,
      activeSnapshotCapabilities: null,
      productPresets: [productItems[1]],
      experimentalPresets: filterCompatibleExperimentalPresets([], false),
    };
    expect(projectCompatiblePresetsEmptyState(catalog, false)).toBe("no-compatible");
  });
});
