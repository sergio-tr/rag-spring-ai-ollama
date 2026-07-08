import { describe, expect, it } from "vitest";
import {
  RETRIEVAL_OVERRIDE_MODE_KEY,
  sanitizeRuntimeOverridePatch,
} from "./retrieval-override-mode";
import {
  classifyPresetProductTier,
  isPresetHardBlockedFromSelector,
  isPresetVisibleInSelector,
  listDefaultVisiblePresetIds,
  P0_PRESET_ID,
  P1_PRESET_ID,
  DEMO_NAIVE_PRESET_ID,
  DEMO_WORST_PRESET_ID,
} from "./preset-product-selection";

describe("closeout D — non-retrieval runtime patches", () => {
  it("no-retrieval preset payload does not send topK/threshold overrides", () => {
    const patch = sanitizeRuntimeOverridePatch(
      {
        [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom",
        topK: 12,
        similarityThreshold: 0.4,
        reasoningEnabled: true,
      },
      false,
    );
    expect(patch).toEqual({ reasoningEnabled: true });
  });

  it("keeps retrieval overrides when retrieval is enabled", () => {
    expect(
      sanitizeRuntimeOverridePatch(
        { [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom", topK: 8, similarityThreshold: 0.5 },
        true,
      ),
    ).toEqual({
      [RETRIEVAL_OVERRIDE_MODE_KEY]: "custom",
      topK: 8,
      similarityThreshold: 0.5,
    });
  });
});

describe("closeout D — STRUCT preset visibility", () => {
  const index = {
    materializationStrategy: "STRUCTURED_SEARCH",
    supportsMetadata: true,
  };

  it("does not show normal retrieval or full-corpus presets on STRUCT projects", () => {
    expect(isPresetHardBlockedFromSelector(P1_PRESET_ID, index)).toBe(true);
    expect(isPresetHardBlockedFromSelector(DEMO_NAIVE_PRESET_ID, index)).toBe(true);
    expect(isPresetVisibleInSelector(P0_PRESET_ID, index, true, false)).toBe(true);
    expect(isPresetVisibleInSelector(P1_PRESET_ID, index, true, true)).toBe(false);
  });

  it("recommends direct LLM baseline only for STRUCT", () => {
    expect(classifyPresetProductTier(P0_PRESET_ID, index, true)).toBe("recommended");
    expect(classifyPresetProductTier(DEMO_WORST_PRESET_ID, index, true)).toBe("recommended");
    expect(classifyPresetProductTier(P1_PRESET_ID, index, true)).toBe("incompatible");
    expect(listDefaultVisiblePresetIds([P0_PRESET_ID, P1_PRESET_ID, DEMO_WORST_PRESET_ID], index)).toEqual([
      P0_PRESET_ID,
      DEMO_WORST_PRESET_ID,
    ]);
  });
});
