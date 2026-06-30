import { describe, expect, it } from "vitest";
import { formatPresetDisplay } from "./lab-benchmark-labels";

describe("lab-benchmark-labels product display", () => {
  it("formatPresetDisplay maps Demo system codes to product names", () => {
    expect(formatPresetDisplay("Demo_Best", "Demo_Best")).toBe("Production assistant configuration");
  });

  it("formatPresetDisplay uses functional preset names without P-code prefix", () => {
    expect(formatPresetDisplay("P4", "Hybrid retrieval")).toBe("Chunk retrieval with metadata");
  });
});
