import { describe, expect, it, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore, type ChatToolbarApi } from "@/features/chat/store/chat-toolbar.store";
import { IntlTestProvider } from "@/test-utils/intl";

const hooksMock = vi.hoisted(() => ({
  useProjectIndexProfile: vi.fn(),
  useActiveProjectSnapshot: vi.fn(),
  useRuntimeConfigCapabilities: vi.fn(),
  useClassifierModelsQuery: vi.fn(),
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
  useClassifierModelsQuery: (...args: unknown[]) => hooksMock.useClassifierModelsQuery(...args),
}));

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <IntlTestProvider>
        <ChatConfigurationPanelContent />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

function baseToolbarApi(overrides: Partial<ChatToolbarApi> = {}): ChatToolbarApi {
  return {
    projectId: "p1",
    conversationId: "c1",
    runtimeOverride: {},
    runtimeState: {
      conversationId: "c1",
      selectedPresetId: null,
      effectivePresetId: "preset",
      preset: {
        kind: "DEFAULT",
        code: null,
        label: "Recommended Default",
        chatSelectable: true,
        supported: true,
        supportStatus: null,
        reasonIfUnsupported: null,
      },
      baseEffectiveConfig: { topK: 8, similarityThreshold: 0.25, useRetrieval: true },
      effectiveConfig: { topK: 8, similarityThreshold: 0.25, useRetrieval: true, llmModel: "m1" },
      effectiveRetrievalParameters: {
        topK: 8,
        similarityThreshold: 0.25,
        topKSource: "USER_DEFAULTS",
        similarityThresholdSource: "USER_DEFAULTS",
      },
      conversationLlmModel: null,
      conversationClassifierModelId: null,
      conversationModelsPinned: false,
      configurationMode: "PRESET" as const,
      runtimeOverride: {},
      manualOverrideKeys: [],
      isCustom: false,
      validation: { valid: true, supported: true, errors: [], warnings: [] },
      selectedWorkflow: "DOCUMENT_DENSE",
      indexCompatibility: {
        activeProjectSnapshotId: "snap-1",
        activeConversationSnapshotId: null,
        activeIndexProfileHash: "hash-1",
        activeIndexProfile: {},
        hasActiveIndex: true,
        compatibleWithPreset: true,
        compatibilityStatus: "COMPATIBLE",
        activeSnapshotCapabilities: {
          materializationStrategy: "CHUNK_LEVEL",
          supportsMetadata: false,
          embeddingModelId: "mxbai-embed-large",
          chunkMaxChars: 400,
          chunkOverlap: 40,
        },
        presetIndexRequirements: null,
      },
      requiresReindex: false,
    },
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
    presetSelectValue: "",
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
    presetLabelOpts: {
      systemSuffix: "",
      recommendedDefault: "",
      defaultConfiguration: "",
    },
    limitDocs: false,
    onLimitDocsChange: vi.fn(),
    limitDocsDisabled: false,
    limitDocsToggleNotice: null,
    patchConvPending: false,
    uploadPending: false,
    uploadError: null,
    uploadNotice: null,
    ...overrides,
  };
}

describe("AssistantConfigurationEffectiveRetrievalValues", () => {
  beforeEach(() => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: { materializationStrategy: "CHUNK_LEVEL" } });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: { id: "snap-1" } });
    hooksMock.useClassifierModelsQuery.mockReturnValue({ data: { models: [] } });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          {
            key: "useRetrieval",
            label: "Use retrieval",
            category: "RUNTIME_HOT_SWAPPABLE",
            visibleInChat: true,
            configurableInChat: true,
            implemented: true,
            engineWired: true,
            displayOrder: 1,
            requires: [],
            excludes: [],
            requiresIndexSnapshot: false,
            requiresReindexWhenChanged: false,
          },
        ],
      },
    });
    useChatToolbarStore.setState({
      api: baseToolbarApi(),
    });
  });

  it("shows effective retrieval values and source in compact summary", () => {
    renderPanel();
    const summary = screen.getByTestId("chat-config-summary-retrieval");
    expect(summary.textContent).toContain("8");
    expect(summary.textContent).toContain("Account or project defaults");
  });
});
