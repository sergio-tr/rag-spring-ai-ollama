import { describe, expect, it } from "vitest";
import { formatPresetDisplay } from "./lab-benchmark-labels";

describe("lab-benchmark-labels product display", () => {
  it("formatPresetDisplay maps Demo system codes to product names", () => {
    expect(formatPresetDisplay("Demo_Best", "Demo_Best")).toBe("Production assistant configuration");
  });

  it("formatPresetDisplay keeps P-code labels for evaluation rows", () => {
    expect(formatPresetDisplay("P4", "Hybrid retrieval")).toBe("P4 — Hybrid retrieval");
  });
});
