import { describe, expect, it } from "vitest";
import { toProductPresetDisplayName } from "./product-preset-labels";

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
