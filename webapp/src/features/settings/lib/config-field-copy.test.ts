import { describe, expect, it, vi } from "vitest";
import { labelConfigField } from "./config-field-copy";

describe("labelConfigField", () => {
  const t = vi.fn((key: string) => `t:${key}`);

  it.each([
    ["topK", "t:projectConfigFieldTopK"],
    ["similarityThreshold", "t:projectConfigFieldSimilarityThreshold"],
    ["llmModel", "t:projectConfigFieldLlmModel"],
    ["llmSystemPrompt", "t:instructionsSystemLabel"],
    ["embeddingModel", "t:projectConfigFieldEmbeddingModel"],
    ["llmTemperature", "t:projectConfigFieldTemperature"],
    ["temperature", "t:projectConfigFieldTemperature"],
    ["expansionEnabled", "t:projectConfigFieldExpansionEnabled"],
    ["nerEnabled", "t:projectConfigFieldNerEnabled"],
    ["toolsEnabled", "t:projectConfigFieldToolsEnabled"],
    ["metadataEnabled", "t:projectConfigFieldMetadataEnabled"],
  ] as const)("maps %s to translated label", (fieldKey, expected) => {
    expect(labelConfigField(fieldKey, t)).toBe(expected);
  });

  it("falls back to the raw field key for unknown keys", () => {
    expect(labelConfigField("futureFlag", t)).toBe("futureFlag");
  });
});
