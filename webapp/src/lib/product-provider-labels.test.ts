import { describe, expect, it } from "vitest";
import { productProviderLabel, productProviderLabelsFromSettings } from "./product-provider-labels";

describe("productProviderLabel", () => {
  const labels = productProviderLabelsFromSettings((key) =>
    key === "configProviderOpenAiCompatible" ? "Configured model provider" : "Local model provider",
  );

  it("maps provider enums to product labels", () => {
    expect(productProviderLabel("OPENAI_COMPATIBLE", labels)).toBe("Configured model provider");
    expect(productProviderLabel("OLLAMA_NATIVE", labels)).toBe("Local model provider");
    expect(productProviderLabel("UNKNOWN", labels)).toBeNull();
    expect(productProviderLabel(undefined, labels)).toBeNull();
  });
});
