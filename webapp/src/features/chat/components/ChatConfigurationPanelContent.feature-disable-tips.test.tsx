import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import type { DisabledRuntimeFeatureDto } from "@/types/api";

const hooksMock = vi.hoisted(() => ({
  useRuntimeConfigCapabilities: vi.fn(),
  useProjectIndexProfile: vi.fn(),
  useActiveProjectSnapshot: vi.fn(),
  useMeEffectiveEmbeddingDefaults: vi.fn(),
}));

vi.mock("@/features/projects/hooks/use-project-index-profile", () => ({
  useProjectIndexProfile: (...args: unknown[]) => hooksMock.useProjectIndexProfile(...args),
}));
vi.mock("@/features/projects/hooks/use-active-project-snapshot", () => ({
  useActiveProjectSnapshot: (...args: unknown[]) => hooksMock.useActiveProjectSnapshot(...args),
}));
vi.mock("@/features/chat/hooks/use-runtime-config-capabilities", () => ({
  useRuntimeConfigCapabilities: (...args: unknown[]) => hooksMock.useRuntimeConfigCapabilities(...args),
}));
vi.mock("@/features/lab/hooks/use-classifier-registry", () => ({
  useClassifierModelsQuery: () => ({ data: [], isError: false, isLoading: false }),
}));
vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", () => ({
  useMeEffectiveEmbeddingDefaults: (...args: unknown[]) => hooksMock.useMeEffectiveEmbeddingDefaults(...args),
}));

const stubCap = (key: string, requires: string[] = [], excludes: string[] = [], engineWired = true) => ({
  key,
  label: key,
  description: "",
  category: "RUNTIME_HOT_SWAPPABLE" as const,
  visibleInChat: true,
  configurableInChat: true,
  implemented: true,
  engineWired,
  supportMode: null,
  displayOrder: 10,
  requires,
  excludes,
  requiresIndexSnapshot: false,
  requiresReindexWhenChanged: false,
  reasonIfDisabled: null as string | null,
  reasonIfNotImplemented: null as string | null,
});

function disabled(key: string, reasonCode: string, reason: string): DisabledRuntimeFeatureDto {
  return { key, reasonCode, reason };
}

function baseRuntimeState(
  effectiveConfig: Record<string, unknown>,
  disabledFeatures: DisabledRuntimeFeatureDto[] = [],
  indexMaterialization = "CHUNK_LEVEL",
) {
  return {
    conversationId: "c1",
    selectedPresetId: "preset-1",
    effectivePresetId: "preset-1",
    preset: {
      kind: "PRODUCT" as const,
      code: "P0",
      label: "Baseline",
      chatSelectable: true,
      supported: true,
      supportStatus: null,
      reasonIfUnsupported: null,
    },
    baseEffectiveConfig: effectiveConfig,
    effectiveConfig,
    effectiveRetrievalParameters: {
      topK: 5,
      similarityThreshold: 0.9,
      topKSource: "PRESET_LOCKED",
      similarityThresholdSource: "PRESET_LOCKED",
    },
    conversationLlmModel: null,
    conversationClassifierModelId: null,
    conversationModelsPinned: false,
    configurationMode: "PRESET" as const,
    runtimeOverride: {},
    manualOverrideKeys: [],
    isCustom: false,
    validation: { valid: true, supported: true, errors: [], warnings: [] },
    selectedWorkflow: "DirectLlmWorkflow",
    indexCompatibility: {
      activeProjectSnapshotId: "snap-1",
      activeConversationSnapshotId: null,
      activeIndexProfileHash: "hash-1",
      activeIndexProfile: { materializationStrategy: indexMaterialization },
      hasActiveIndex: true,
      activeSnapshotCapabilities: {
        materializationStrategy: indexMaterialization,
        supportsMetadata: false,
        embeddingModelId: "emb",
        chunkMaxChars: 400,
        chunkOverlap: 40,
      },
      presetIndexRequirements: null,
      compatibleWithPreset: true,
      compatibilityStatus: "COMPATIBLE",
    },
    requiresReindex: false,
    disabledRuntimeFeatures: disabledFeatures,
    blockingIssues: [],
  };
}

function allCaps() {
  return [
    stubCap("useRetrieval"),
    stubCap("naiveFullCorpusInPromptEnabled", [], ["useRetrieval"]),
    stubCap("expansionEnabled"),
    stubCap("toolsEnabled"),
    stubCap("functionCallingEnabled", [], ["naiveFullCorpusInPromptEnabled"]),
    stubCap("useAdvisor", ["useRetrieval"]),
    stubCap("rankerEnabled", ["useRetrieval"]),
    stubCap("postRetrievalEnabled", ["useRetrieval"]),
    stubCap("metadataEnabled", ["toolsEnabled"]),
    stubCap("nerEnabled"),
    stubCap("reasoningEnabled"),
    stubCap("adaptiveRoutingEnabled"),
    stubCap("judgeEnabled"),
  ];
}

function renderSubject() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider locale="en">
        <ChatConfigurationPanelContent />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
  fireEvent.click(screen.getByTestId("chat-config-edit-button"));
}

function expectShortTip(key: string, text: string) {
  const tip = screen.getByTestId(`chat-runtime-disable-tip-${key}`);
  expect(tip).toHaveTextContent(text);
  expect(tip.textContent!.length).toBeLessThan(60);
}

describe("ChatConfigurationPanelContent feature disable tips", () => {
  beforeEach(() => {
    hooksMock.useMeEffectiveEmbeddingDefaults.mockReturnValue({ data: null });
    hooksMock.useProjectIndexProfile.mockReturnValue({
      data: { materializationStrategy: "CHUNK_LEVEL", metadataEnabled: false },
      isLoading: false,
      isError: false,
    });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: { id: "snap-1" }, isLoading: false, isError: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: { capabilities: allCaps() },
      isLoading: false,
    });

    useChatToolbarStore.setState({
      api: {
        projectId: "p1",
        conversationId: "c1",
        runtimeOverride: {},
        runtimeState: baseRuntimeState({ useRetrieval: false }),
        runtimeStateLoading: false,
        runtimeStateError: null,
        refreshRuntimeState: vi.fn(),
        saveRuntimeOverride: vi.fn(),
        clearRuntimeOverride: vi.fn(),
        openDeleteForActiveConversation: vi.fn(),
        openMoveDialog: vi.fn(),
        openDocumentsSheet: vi.fn(),
        onAddDocuments: vi.fn(),
        llmModelChoice: "",
        setLlmModelChoice: vi.fn(),
        classifierModelChoice: "",
        setClassifierModelChoice: vi.fn(),
        selectableLlmModels: [],
        selectableLlmModelsLoading: false,
        selectableLlmModelsEffectiveProvider: undefined,
        modelsError: false,
        modelsErrorMessage: "",
        presetSelectValue: "preset-1",
        onPresetChange: vi.fn(),
        presets: [],
        presetsError: false,
        presetsLoading: false,
        projectCompatiblePresets: null,
        compatibleProductPresets: [],
        compatibleExperimentalPresets: [],
        experimentalPresets: [],
        experimentalPresetsLoading: false,
        experimentalPresetsError: false,
        presetSelectDisabled: false,
        syntheticPresetOptionNeeded: false,
        presetLabelOpts: { systemSuffix: "", recommendedDefault: "", defaultConfiguration: "" },
        limitDocs: false,
        onLimitDocsChange: vi.fn(),
        limitDocsDisabled: false,
        limitDocsToggleNotice: null,
        patchConvPending: false,
        uploadPending: false,
        uploadError: null,
        uploadNotice: null,
      },
    });
  });

  it("Advisor disabled without retrieval shows Requires retrieval", () => {
    renderSubject();
    expectShortTip("useAdvisor", "Requires retrieval");
  });

  it("Ranker disabled without retrieval shows Requires retrieval", () => {
    renderSubject();
    expectShortTip("rankerEnabled", "Requires retrieval");
  });

  it("Post-retrieval disabled without retrieval shows Requires retrieval", () => {
    renderSubject();
    expectShortTip("postRetrievalEnabled", "Requires retrieval");
  });

  it("Metadata disabled without tools shows Requires tools", () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: baseRuntimeState(
          { useRetrieval: true, toolsEnabled: false },
          [disabled("metadataEnabled", "REQUIRES_toolsEnabled", "Requires toolsEnabled=true.")],
        ),
      },
    }));
    renderSubject();
    expectShortTip("metadataEnabled", "Requires tools");
  });

  it("Retrieval disabled on STRUCTURED_SEARCH shows Not supported by this index", () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: baseRuntimeState(
          { useRetrieval: false },
          [
            disabled(
              "useRetrieval",
              "STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED",
              "Structured-search projects do not support vector retrieval.",
            ),
          ],
          "STRUCTURED_SEARCH",
        ),
      },
    }));
    renderSubject();
    expectShortTip("useRetrieval", "Not supported by this index");
  });

  it("Full-context disabled on STRUCTURED_SEARCH shows Requires vector chunks", () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: baseRuntimeState(
          { useRetrieval: false },
          [
            disabled(
              "naiveFullCorpusInPromptEnabled",
              "STRUCTURED_SEARCH_FULL_CONTEXT_UNSUPPORTED",
              "Full-context mode is unavailable because this project has no vector chunks.",
            ),
          ],
          "STRUCTURED_SEARCH",
        ),
      },
    }));
    renderSubject();
    expectShortTip("naiveFullCorpusInPromptEnabled", "Requires vector chunks");
  });

  it("shows query expansion toggle when engine-wired", () => {
    renderSubject();
    expect(screen.getByTestId("chat-runtime-toggle-expansionEnabled")).toBeInTheDocument();
  });

  it("Function calling with full-context shows Incompatible with full-context", () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: baseRuntimeState(
          { useRetrieval: false, naiveFullCorpusInPromptEnabled: true },
          [
            disabled(
              "functionCallingEnabled",
              "EXCLUDES_naiveFullCorpusInPromptEnabled",
              "Cannot be enabled with naiveFullCorpusInPromptEnabled=true.",
            ),
          ],
        ),
      },
    }));
    renderSubject();
    expectShortTip("functionCallingEnabled", "Incompatible with full-context");
  });

  it("does not render long backend validation paragraphs inside toggle rows", () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...baseRuntimeState(
            { useRetrieval: false },
            [
              disabled(
                "useRetrieval",
                "STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED",
                "Structured-search projects do not support vector retrieval.",
              ),
            ],
            "STRUCTURED_SEARCH",
          ),
          blockingIssues: [
            {
              code: "STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED",
              field: "useRetrieval",
              message: "Structured-search projects do not support vector retrieval.",
              severity: "ERROR",
            },
          ],
        },
      },
    }));
    renderSubject();
    expectShortTip("useRetrieval", "Not supported by this index");
    expect(
      screen.queryByText("Structured-search projects do not support vector retrieval."),
    ).not.toBeInTheDocument();
  });
});
