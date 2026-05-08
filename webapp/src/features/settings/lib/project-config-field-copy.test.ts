import { describe, expect, it, vi } from "vitest";
import { labelProjectConfigField } from "./project-config-field-copy";

describe("labelProjectConfigField", () => {
  it("maps known keys via translator", () => {
    const t = vi.fn((k: string) => `translated:${k}`);
    expect(labelProjectConfigField("topK", t)).toBe("translated:projectConfigFieldTopK");
    expect(t).toHaveBeenCalledWith("projectConfigFieldTopK");
  });

  it("maps all other known keys via translator", () => {
    const t = vi.fn((k: string) => `translated:${k}`);
    expect(labelProjectConfigField("similarityThreshold", t)).toBe("translated:projectConfigFieldSimilarityThreshold");
    expect(labelProjectConfigField("llmModel", t)).toBe("translated:projectConfigFieldLlmModel");
    expect(labelProjectConfigField("expansionEnabled", t)).toBe("translated:projectConfigFieldExpansionEnabled");
    expect(labelProjectConfigField("nerEnabled", t)).toBe("translated:projectConfigFieldNerEnabled");
    expect(labelProjectConfigField("toolsEnabled", t)).toBe("translated:projectConfigFieldToolsEnabled");
    expect(labelProjectConfigField("metadataEnabled", t)).toBe("translated:projectConfigFieldMetadataEnabled");
  });

  it("falls back to raw key when unknown", () => {
    const t = vi.fn(() => "unused");
    expect(labelProjectConfigField("futureKey", t)).toBe("futureKey");
    expect(t).not.toHaveBeenCalled();
  });
});
