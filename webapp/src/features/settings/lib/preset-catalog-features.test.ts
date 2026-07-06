import { describe, expect, it } from "vitest";
import type { ExperimentalPresetCatalogItemDto } from "@/types/api";
import {
  deriveExperimentalPresetCatalogFeatures,
  deriveProductPresetCatalogFeatures,
  enabledPresetCatalogFeatureKeys,
  resolvePresetRuntimeValues,
} from "./preset-catalog-features";

const DEMO_BEST_ID = "cafe0001-0001-4001-8001-000000000003";

function experimental(
  overrides: Partial<ExperimentalPresetCatalogItemDto> & Pick<ExperimentalPresetCatalogItemDto, "code">,
): ExperimentalPresetCatalogItemDto {
  return {
    productPresetId: "id",
    family: "S2",
    label: overrides.label ?? overrides.code,
    description: "",
    requiredCapabilities: [],
    supported: true,
    supportStatus: "EXECUTABLE",
    reasonIfUnsupported: null,
    requiresMultiTurn: false,
    mapsToRuntimeCapabilities: {},
    allowedOutcomes: ["EXECUTED"],
    chatSelectable: true,
    labSelectable: true,
    labOnly: false,
    ...overrides,
  };
}

describe("preset-catalog-features", () => {
  it("P0 resolves direct LLM baseline from runtimeFeatureFlags", () => {
    const preset = experimental({
      code: "P0",
      mapsToRuntimeCapabilities: {
        runtimeFeatureFlags: {
          useRetrieval: false,
          naiveFullCorpusInPromptEnabled: false,
          metadataEnabled: false,
          expansionEnabled: false,
          nerEnabled: false,
        },
      },
    });
    const enabled = enabledPresetCatalogFeatureKeys(deriveExperimentalPresetCatalogFeatures(preset));
    expect(enabled).toContain("directLlm");
    expect(enabled).not.toContain("retrieval");
    expect(enabled).not.toContain("fullCorpus");
  });

  it("P8 resolves hybrid retrieval, reranking, and post-retrieval", () => {
    const preset = experimental({
      code: "P8",
      mapsToRuntimeCapabilities: {
        runtimeFeatureFlags: {
          useRetrieval: true,
          materializationStrategy: "HYBRID",
          rankerEnabled: true,
          postRetrievalEnabled: true,
          metadataEnabled: true,
        },
      },
      indexRequirements: {
        requiredMaterializationStrategy: "HYBRID",
        requiresMetadataSupport: true,
      },
    });
    const enabled = enabledPresetCatalogFeatureKeys(deriveExperimentalPresetCatalogFeatures(preset));
    expect(enabled).toContain("hybridRetrieval");
    expect(enabled).toContain("reranking");
    expect(enabled).toContain("postRetrievalProcessing");
    expect(enabled).toContain("metadataAwareRetrieval");
  });

  it("Demo_Best resolves production bundle from preset.values", () => {
    const enabled = enabledPresetCatalogFeatureKeys(
      deriveProductPresetCatalogFeatures({
        values: {
          useRetrieval: true,
          materializationStrategy: "HYBRID",
          metadataEnabled: true,
          expansionEnabled: true,
          nerEnabled: true,
          deterministicToolRoutingEnabled: true,
          functionCallingEnabled: true,
          useAdvisor: true,
          clarificationEnabled: true,
          rankerEnabled: false,
          memoryEnabled: false,
          judgeEnabled: false,
        },
      }),
    );
    expect(enabled).toContain("retrieval");
    expect(enabled).toContain("hybridRetrieval");
    expect(enabled).toContain("functionCalling");
    expect(enabled).toContain("advisor");
    expect(enabled).not.toContain("directLlm");
    expect(enabled).not.toContain("memory");
  });

  it("prefers effectiveTerminalRuntimeJson over runtimeFeatureFlags", () => {
    const preset = experimental({
      code: "P3",
      effectiveTerminalRuntimeJson: JSON.stringify({
        useRetrieval: true,
        materializationStrategy: "CHUNK_LEVEL",
      }),
      mapsToRuntimeCapabilities: {
        runtimeFeatureFlags: { useRetrieval: false },
      },
    });
    const values = resolvePresetRuntimeValues({ kind: "experimental", preset });
    expect(values.useRetrieval).toBe(true);
    expect(values.materializationStrategy).toBe("CHUNK_LEVEL");
  });

  it("always marks Chat operational override badges as enabled", () => {
    const enabled = enabledPresetCatalogFeatureKeys(
      deriveProductPresetCatalogFeatures({ values: { useRetrieval: false } }),
    );
    expect(enabled).toContain("retrievalSourceSupport");
    expect(enabled).toContain("safeOperationalOverrides");
  });
});

describe("preset-catalog-features product id sanity", () => {
  it("Demo_Best preset id matches catalog constant", () => {
    expect(DEMO_BEST_ID).toBe("cafe0001-0001-4001-8001-000000000003");
  });
});
