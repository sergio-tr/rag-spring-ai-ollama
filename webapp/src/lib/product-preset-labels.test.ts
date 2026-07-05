import { describe, expect, it } from "vitest";
import {
  productPresetDescription,
  productPresetInternalCodeChip,
  productPresetLabel,
  toProductPresetDisplayName,
} from "./product-preset-labels";

describe("toProductPresetDisplayName", () => {
  it("maps seeded demo system preset names to product labels", () => {
    expect(toProductPresetDisplayName("Demo_Best")).toBe("Production assistant configuration");
    expect(toProductPresetDisplayName("Demo_Worst")).toBe("Basic baseline configuration");
    expect(toProductPresetDisplayName("Demo_NaiveFullCorpus")).toBe("Full-context baseline");
  });

  it("passes through custom preset names unchanged", () => {
    expect(toProductPresetDisplayName("My team preset")).toBe("My team preset");
  });
});

describe("productPresetLabel", () => {
  it("maps experimental preset codes to functional labels", () => {
    expect(productPresetLabel("P0")).toBe("Indexed text answers");
    expect(productPresetLabel("P4")).toBe("Chunk retrieval with metadata");
    expect(productPresetLabel("P15")).toBe("Integrated single-turn composition");
  });

  it("primary labels do not start with P-code prefix", () => {
    for (const code of ["P0", "P1", "P4", "P14", "P15"]) {
      const label = productPresetLabel(code);
      expect(label).not.toMatch(/^P\d+/);
    }
  });

  it("prefers resolved i18n copy when translator returns a value", () => {
    const t = (key: string) => (key === "presetDisplay.P4" ? "Metadata retrieval (i18n)" : key);
    expect(productPresetLabel("P4", t)).toBe("Metadata retrieval (i18n)");
  });

  it("productPresetDescription uses i18n description keys", () => {
    const t = (key: string) =>
      key === "presetDisplay.P4Description" ? "Chunk-level retrieval enriched with metadata." : key;
    expect(productPresetDescription("P4", t)).toBe("Chunk-level retrieval enriched with metadata.");
  });

  it("productPresetLabel ignores unresolved i18n keys", () => {
    const t = (key: string) => key;
    expect(productPresetLabel("P4", t)).toBe("Chunk retrieval with metadata");
    expect(productPresetDescription("P99", t)).toBe("");
  });

  it("productPresetDescription maps Demo_Best to accurate capability copy", () => {
    expect(productPresetDescription("Demo_Best")).toContain("Hybrid retrieval");
    expect(productPresetDescription("Demo_Best")).toContain("Memory, judge, and extended reasoning");
  });

  it("productPresetInternalCodeChip exposes P-codes only for experimental presets", () => {
    expect(productPresetInternalCodeChip("P4")).toBe("P4");
    expect(productPresetInternalCodeChip("demo_best")).toBeNull();
  });
});
