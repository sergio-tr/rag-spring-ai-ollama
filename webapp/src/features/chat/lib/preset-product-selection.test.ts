import { describe, expect, it } from "vitest";
import {
  CATALOG_PRESET_IDS,
  classifyPresetProductTier,
  DEMO_BEST_PRESET_ID,
  DEMO_NAIVE_PRESET_ID,
  DEMO_WORST_PRESET_ID,
  HYBRID_ADVANCED_PRESET_IDS,
  isPresetHardBlockedFromSelector,
  isPresetVisibleInSelector,
  listDefaultVisiblePresetIds,
  METADATA_REQUIRED_PRESET_IDS,
  P0_PRESET_ID,
  P1_PRESET_ID,
  P2_PRESET_ID,
  P3_PRESET_ID,
  P4_PRESET_ID,
  P5_PRESET_ID,
  P6_PRESET_ID,
  P7_PRESET_ID,
  P8_PRESET_ID,
  type ProjectIndexCaps,
} from "./preset-product-selection";

function visible(caps: ProjectIndexCaps): string[] {
  return listDefaultVisiblePresetIds(CATALOG_PRESET_IDS, caps);
}

function hidden(caps: ProjectIndexCaps): string[] {
  const shown = new Set(visible(caps));
  return CATALOG_PRESET_IDS.filter((id) => !shown.has(id));
}

describe("preset visibility matrix — DOCUMENT_LEVEL", () => {
  const noMeta: ProjectIndexCaps = {
    materializationStrategy: "DOCUMENT_LEVEL",
    supportsMetadata: false,
  };
  const withMeta: ProjectIndexCaps = {
    materializationStrategy: "DOCUMENT_LEVEL",
    supportsMetadata: true,
  };

  const expectedVisible = [
    P0_PRESET_ID,
    P1_PRESET_ID,
    P2_PRESET_ID,
    DEMO_WORST_PRESET_ID,
    DEMO_NAIVE_PRESET_ID,
  ];

  it("no metadata — shows direct LLM, full corpus, document RAG only", () => {
    expect(visible(noMeta).sort()).toEqual(expectedVisible.sort());
    expect(classifyPresetProductTier(P3_PRESET_ID, noMeta, true)).toBe("incompatible");
    expect(classifyPresetProductTier(DEMO_BEST_PRESET_ID, noMeta, true)).toBe("incompatible");
  });

  it("with metadata — same as no metadata (no document-metadata presets implemented)", () => {
    expect(visible(withMeta).sort()).toEqual(expectedVisible.sort());
  });
});

describe("preset visibility matrix — CHUNK_LEVEL", () => {
  const noMeta: ProjectIndexCaps = {
    materializationStrategy: "CHUNK_LEVEL",
    supportsMetadata: false,
  };
  const withMeta: ProjectIndexCaps = {
    materializationStrategy: "CHUNK_LEVEL",
    supportsMetadata: true,
  };

  const expectedNoMeta = [
    P0_PRESET_ID,
    P1_PRESET_ID,
    P3_PRESET_ID,
    DEMO_WORST_PRESET_ID,
    DEMO_NAIVE_PRESET_ID,
  ];

  const expectedWithMeta = [
    ...expectedNoMeta,
    P4_PRESET_ID,
    P5_PRESET_ID,
    P6_PRESET_ID,
    P7_PRESET_ID,
  ];

  it("no metadata — chunk RAG and direct/full corpus; hides metadata and tools presets", () => {
    expect(visible(noMeta).sort()).toEqual(expectedNoMeta.sort());
    for (const id of METADATA_REQUIRED_PRESET_IDS) {
      expect(isPresetHardBlockedFromSelector(id, noMeta)).toBe(true);
      expect(isPresetVisibleInSelector(id, noMeta, true, true)).toBe(false);
    }
    expect(hidden(noMeta)).toContain(P2_PRESET_ID);
    expect(hidden(noMeta)).toContain(DEMO_BEST_PRESET_ID);
    expect([...HYBRID_ADVANCED_PRESET_IDS].every((id) => hidden(noMeta).includes(id))).toBe(true);
  });

  it("with metadata — adds metadata RAG through deterministic tools", () => {
    expect(visible(withMeta).sort()).toEqual(expectedWithMeta.sort());
    expect(classifyPresetProductTier(P4_PRESET_ID, withMeta, true)).toBe("recommended");
    expect(classifyPresetProductTier(P7_PRESET_ID, withMeta, true)).toBe("recommended");
    expect(classifyPresetProductTier(P8_PRESET_ID, withMeta, true)).toBe("incompatible");
    expect(classifyPresetProductTier(DEMO_BEST_PRESET_ID, withMeta, true)).toBe("incompatible");
  });
});

describe("preset visibility matrix — HYBRID", () => {
  const noMeta: ProjectIndexCaps = {
    materializationStrategy: "HYBRID",
    supportsMetadata: false,
  };
  const withMeta: ProjectIndexCaps = {
    materializationStrategy: "HYBRID",
    supportsMetadata: true,
  };

  const expectedNoMeta = [
    P0_PRESET_ID,
    P1_PRESET_ID,
    P3_PRESET_ID,
    DEMO_WORST_PRESET_ID,
    DEMO_NAIVE_PRESET_ID,
  ];

  it("no metadata — basic chunk retrieval; no metadata/tools or hybrid-advanced presets", () => {
    expect(visible(noMeta).sort()).toEqual(expectedNoMeta.sort());
    for (const id of METADATA_REQUIRED_PRESET_IDS) {
      expect(isPresetVisibleInSelector(id, noMeta, true, false)).toBe(false);
      expect(isPresetVisibleInSelector(id, noMeta, true, true)).toBe(false);
    }
    expect(isPresetVisibleInSelector(P8_PRESET_ID, noMeta, true, false)).toBe(false);
    expect(isPresetVisibleInSelector(DEMO_BEST_PRESET_ID, noMeta, true, false)).toBe(false);
  });

  it("with metadata — direct LLM, chunk/metadata/tools, hybrid advanced, Demo_Best", () => {
    const shown = visible(withMeta);
    expect(shown).toContain(P0_PRESET_ID);
    expect(shown).toContain(DEMO_WORST_PRESET_ID);
    expect(shown).toContain(P3_PRESET_ID);
    expect(shown).toContain(P4_PRESET_ID);
    expect(shown).toContain(P7_PRESET_ID);
    expect(shown).toContain(P8_PRESET_ID);
    expect(shown).toContain(DEMO_BEST_PRESET_ID);
    expect(shown).not.toContain(P1_PRESET_ID);
    expect(shown).not.toContain(DEMO_NAIVE_PRESET_ID);
    expect(shown).not.toContain(P2_PRESET_ID);
    expect(classifyPresetProductTier(DEMO_BEST_PRESET_ID, withMeta, true)).toBe("recommended");
    expect(classifyPresetProductTier([...HYBRID_ADVANCED_PRESET_IDS][0]!, withMeta, true)).toBe(
      "recommended",
    );
    expect(classifyPresetProductTier(P2_PRESET_ID, withMeta, true)).toBe("incompatible");
    expect(isPresetVisibleInSelector(P2_PRESET_ID, withMeta, true, false)).toBe(false);
  });
});

describe("preset visibility matrix — STRUCTURED_SEARCH", () => {
  const index: ProjectIndexCaps = {
    materializationStrategy: "STRUCTURED_SEARCH",
    supportsMetadata: true,
  };

  const expectedVisible = [P0_PRESET_ID, DEMO_WORST_PRESET_ID];

  it("shows direct LLM only; never exposes retrieval or full-corpus presets", () => {
    expect(visible(index).sort()).toEqual(expectedVisible.sort());
    expect(isPresetVisibleInSelector(P1_PRESET_ID, index, true, true)).toBe(false);
    expect(isPresetVisibleInSelector(DEMO_NAIVE_PRESET_ID, index, true, true)).toBe(false);
    expect(isPresetVisibleInSelector(P2_PRESET_ID, index, true, false)).toBe(false);
    expect(isPresetVisibleInSelector(P3_PRESET_ID, index, true, true)).toBe(false);
    expect(isPresetVisibleInSelector(DEMO_BEST_PRESET_ID, index, true, true)).toBe(false);
    expect(classifyPresetProductTier(DEMO_BEST_PRESET_ID, index, false)).toBe("incompatible");
  });
});

describe("preset visibility matrix — incompatible toggle", () => {
  it("surfaces backend-incompatible presets only when toggle enabled", () => {
    const index: ProjectIndexCaps = {
      materializationStrategy: "DOCUMENT_LEVEL",
      supportsMetadata: false,
    };
    expect(isPresetVisibleInSelector(P3_PRESET_ID, index, false, false)).toBe(false);
    expect(isPresetVisibleInSelector(P3_PRESET_ID, index, false, true)).toBe(true);
  });
});
