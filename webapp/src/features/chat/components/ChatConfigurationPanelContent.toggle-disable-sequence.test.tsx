import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";

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

const stubCap = (key: string, requires: string[] = []) => ({
  key,
  label: key,
  description: "",
  category: "RUNTIME_HOT_SWAPPABLE" as const,
  visibleInChat: true,
  configurableInChat: true,
  implemented: true,
  engineWired: true,
  supportMode: null,
  displayOrder: 10,
  requires,
  excludes: [],
  requiresIndexSnapshot: false,
  requiresReindexWhenChanged: false,
  reasonIfDisabled: null as string | null,
  reasonIfNotImplemented: null as string | null,
});

function runtimeStateFromEffective(
  effectiveConfig: Record<string, unknown>,
  overrides: Record<string, unknown> = {},
) {
  const baseEffectiveConfig = { useRetrieval: true };
  return {
    conversationId: "c1",
    selectedPresetId: "preset-1",
    effectivePresetId: "preset-1",
    preset: {
      kind: "PRODUCT" as const,
      code: "P4",
      label: "Chunk retrieval",
      chatSelectable: true,
      supported: true,
      supportStatus: null,
      reasonIfUnsupported: null,
    },
    baseEffectiveConfig,
    effectiveConfig: { ...effectiveConfig, ...overrides },
    effectiveRetrievalParameters: {
      topK: 5,
      similarityThreshold: 0.9,
      topKSource: "PRESET_LOCKED",
      similarityThresholdSource: "PRESET_LOCKED",
    },
    conversationLlmModel: null,
    conversationClassifierModelId: null,
    conversationModelsPinned: false,
    configurationMode: Object.keys(overrides).length > 0 ? ("CUSTOM" as const) : ("PRESET" as const),
    runtimeOverride: overrides,
    manualOverrideKeys: Object.keys(overrides),
    isCustom: Object.keys(overrides).length > 0,
    validation: { valid: true, supported: true, errors: [], warnings: [] },
    selectedWorkflow: "dense",
    indexCompatibility: {
      activeProjectSnapshotId: "snap-1",
      activeConversationSnapshotId: null,
      activeIndexProfileHash: "hash-1",
      activeIndexProfile: {},
      hasActiveIndex: true,
      activeSnapshotCapabilities: {
        materializationStrategy: "CHUNK_LEVEL",
        supportsMetadata: false,
        embeddingModelId: "mxbai",
        chunkMaxChars: 400,
        chunkOverlap: 40,
      },
    },
    requiresReindex: false,
    disabledRuntimeFeatures: [],
  };
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
}

describe("ChatConfigurationPanelContent toggle disable sequence", () => {
  const presetEffective = {
    useRetrieval: true,
    useAdvisor: true,
    rankerEnabled: true,
    postRetrievalEnabled: true,
  };

  let saveRuntimeOverride: (next: Record<string, unknown>) => void;

  beforeEach(() => {
    saveRuntimeOverride = vi.fn<(next: Record<string, unknown>) => void>();
    hooksMock.useMeEffectiveEmbeddingDefaults.mockReturnValue({ data: null });
    hooksMock.useProjectIndexProfile.mockReturnValue({
      data: { materializationStrategy: "CHUNK_LEVEL", metadataEnabled: false },
      isLoading: false,
      isError: false,
    });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          stubCap("useRetrieval"),
          stubCap("rankerEnabled", ["useRetrieval"]),
          stubCap("postRetrievalEnabled", ["useRetrieval"]),
          stubCap("useAdvisor", ["useRetrieval"]),
        ],
      },
      isLoading: false,
    });

    useChatToolbarStore.setState({
      api: {
        projectId: "p1",
        conversationId: "c1",
        runtimeOverride: {},
        runtimeState: runtimeStateFromEffective(presetEffective),
        runtimeStateLoading: false,
        runtimeStateError: null,
        refreshRuntimeState: vi.fn(),
        saveRuntimeOverride,
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
        presetLabelOpts: {
          systemSuffix: " (system)",
          recommendedDefault: "Recommended default",
          defaultConfiguration: "Default configuration",
        },
        limitDocs: false,
        onLimitDocsChange: vi.fn(),
        limitDocsDisabled: false,
        limitDocsToggleNotice: null,
        patchConvPending: false,
        uploadPending: false,
        uploadError: null,
        uploadNotice: null,
        documents: [],
      },
    });
  });

  it("disabling Advisor then Ranker sends partial patches and keeps Advisor disabled in effective config", () => {
    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    fireEvent.click(screen.getByTestId("chat-runtime-toggle-useAdvisor"));

    expect(saveRuntimeOverride).toHaveBeenCalledWith({ useAdvisor: false });

    useChatToolbarStore.setState({
      api: {
        ...useChatToolbarStore.getState().api!,
        runtimeState: runtimeStateFromEffective(
          { ...presetEffective, useAdvisor: false },
          { useAdvisor: false, rankerEnabled: true, postRetrievalEnabled: true },
        ),
      },
    });

    fireEvent.click(screen.getByTestId("chat-runtime-toggle-rankerEnabled"));
    expect(saveRuntimeOverride).toHaveBeenLastCalledWith({ rankerEnabled: false });
    expect(screen.getByTestId("chat-runtime-toggle-useAdvisor")).not.toBeChecked();
  });

  it("shows custom configuration badge after first feature edit", () => {
    useChatToolbarStore.setState({
      api: {
        ...useChatToolbarStore.getState().api!,
        runtimeState: runtimeStateFromEffective(
          { ...presetEffective, useAdvisor: false },
          { useAdvisor: false, rankerEnabled: true, postRetrievalEnabled: true },
        ),
      },
    });
    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    expect(screen.getByTestId("chat-custom-state")).toHaveTextContent(
      /Custom configuration for this conversation/i,
    );
  });
});
