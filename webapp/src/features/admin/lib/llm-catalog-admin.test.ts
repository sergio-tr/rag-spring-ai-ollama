import { describe, it, expect } from "vitest";
import type { LlmCatalogModelDto } from "@/types/api";
import {
  catalogDisplayName,
  catalogRuntimeStatusLabel,
  catalogSourceLabel,
  isCatalogConfigured,
  isCatalogIndexingDisabled,
  isCatalogRuntimeNotProbed,
  isCatalogVectorIncompatible,
} from "./llm-catalog-admin";

const embeddingIncompatible: LlmCatalogModelDto = {
  provider: "OLLAMA_NATIVE",
  modelName: "wrong-dim:latest",
  capability: "EMBEDDING",
  available: true,
  selectableByUser: false,
  usableAsDefault: false,
  runtimeStatus: "AVAILABLE",
  runtimeDetail: null,
  embeddingDimensions: 512,
  compatibleWithCurrentVectorStore: false,
  source: "PROPERTIES",
};

describe("llm-catalog-admin", () => {
  it("incompatible embedding marked incompatible", () => {
    expect(isCatalogVectorIncompatible(embeddingIncompatible)).toBe(true);
    expect(isCatalogIndexingDisabled(embeddingIncompatible)).toBe(true);
  });

  it("catalogDisplayName falls back to modelName", () => {
    expect(catalogDisplayName(embeddingIncompatible)).toBe("wrong-dim:latest");
    expect(
      catalogDisplayName({ ...embeddingIncompatible, displayName: "Wrong dimensions" }),
    ).toBe("Wrong dimensions");
  });

  it("isCatalogConfigured treats PROPERTIES source as configured", () => {
    expect(isCatalogConfigured(embeddingIncompatible)).toBe(true);
    expect(isCatalogConfigured({ ...embeddingIncompatible, configured: false })).toBe(false);
    expect(isCatalogConfigured({ ...embeddingIncompatible, source: "LITELLM_CONFIGURED" })).toBe(true);
  });

  it("catalogSourceLabel maps LiteLLM configured source", () => {
    const labels = {
      catalogSourceLitellmConfigured: "LiteLLM catalog",
      catalogSourceConfiguredCatalog: "Configured",
      catalogSourceOllamaLive: "Ollama live",
      catalogSourceUnknown: "Unknown",
      catalogSourceProperties: "Properties",
      catalogRuntimeStatusConfigured: "Configured",
      catalogRuntimeStatusNotProbed: "Not probed",
      catalogRuntimeStatusNotProbedOpenAI: "Configured (remote not probed)",
      catalogRuntimeStatusAvailable: "Available",
      catalogRuntimeStatusUnavailable: "Unavailable",
      catalogRuntimeStatusUnavailableOpenAI: "Unavailable on API",
      catalogRuntimeStatusProbeFailed: "Probe failed",
      catalogRuntimeUnavailable: "Unavailable runtime",
      catalogRuntimeUnavailableOpenAI: "Unavailable on API catalog",
    };
    expect(catalogSourceLabel("LITELLM_CONFIGURED", labels)).toBe("LiteLLM catalog");
    expect(catalogRuntimeStatusLabel("NOT_PROBED", "OPENAI_COMPATIBLE", labels)).toBe(
      "Configured (remote not probed)",
    );
  });

  it("isCatalogRuntimeNotProbed excludes unavailable rows", () => {
    expect(
      isCatalogRuntimeNotProbed({
        ...embeddingIncompatible,
        provider: "OPENAI_COMPATIBLE",
        runtimeStatus: "NOT_PROBED",
      }),
    ).toBe(true);
    expect(
      isCatalogRuntimeNotProbed({
        ...embeddingIncompatible,
        runtimeStatus: "UNAVAILABLE",
      }),
    ).toBe(false);
  });
});
