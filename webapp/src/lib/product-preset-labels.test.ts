import { describe, expect, it } from "vitest";
import { productPresetLabel, toProductPresetDisplayName } from "./product-preset-labels";

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
});
