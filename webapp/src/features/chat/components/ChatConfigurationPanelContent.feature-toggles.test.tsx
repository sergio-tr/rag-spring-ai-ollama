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

const stubCap = (key: string, requires: string[] = [], excludes: string[] = []) => ({
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
  excludes,
  requiresIndexSnapshot: false,
  requiresReindexWhenChanged: false,
  reasonIfDisabled: null as string | null,
  reasonIfNotImplemented: null as string | null,
});

const advancedCap = (key: string) => ({
  ...stubCap(key),
  category: "ADVANCED_RUNTIME" as const,
  supportMode: key === "memoryEnabled" || key === "clarificationEnabled" ? "MULTI_TURN_REQUIRED" : null,
});

function baseRuntimeState(overrides: Record<string, unknown> = {}) {
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
    baseEffectiveConfig: {
      useRetrieval: true,
      expansionEnabled: false,
      nerEnabled: false,
      toolsEnabled: true,
      rankerEnabled: false,
      postRetrievalEnabled: false,
      memoryEnabled: false,
      clarificationEnabled: false,
      judgeEnabled: false,
      functionCallingEnabled: false,
      naiveFullCorpusInPromptEnabled: false,
      useAdvisor: false,
    },
    effectiveConfig: {
      useRetrieval: true,
      expansionEnabled: false,
      nerEnabled: false,
      toolsEnabled: true,
      rankerEnabled: false,
      postRetrievalEnabled: false,
      memoryEnabled: false,
      clarificationEnabled: false,
      judgeEnabled: false,
      functionCallingEnabled: false,
      naiveFullCorpusInPromptEnabled: false,
      useAdvisor: false,
    },
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

describe("ChatConfigurationPanelContent feature toggles", () => {
  beforeEach(() => {
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
          stubCap("expansionEnabled"),
          stubCap("nerEnabled"),
          stubCap("toolsEnabled"),
          stubCap("functionCallingEnabled", [], ["naiveFullCorpusInPromptEnabled"]),
          stubCap("rankerEnabled", ["useRetrieval"]),
          stubCap("postRetrievalEnabled", ["useRetrieval"]),
          stubCap("useAdvisor", ["useRetrieval"]),
          advancedCap("memoryEnabled"),
          advancedCap("clarificationEnabled"),
          stubCap("judgeEnabled"),
        ],
      },
      isLoading: false,
    });

    useChatToolbarStore.setState({
      api: {
        projectId: "p1",
        conversationId: "c1",
        runtimeOverride: {},
        runtimeState: baseRuntimeState(),
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

  it("does not send override when optional add-ons are preset-controlled (deferred)", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride, patchConvPending: false },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    fireEvent.click(screen.getByTestId("chat-runtime-toggle-expansionEnabled"));
    fireEvent.click(screen.getByTestId("chat-runtime-toggle-nerEnabled"));

    expect(saveRuntimeOverride).not.toHaveBeenCalled();
    expect((screen.getByTestId("chat-runtime-toggle-expansionEnabled") as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByTestId("chat-runtime-toggle-nerEnabled") as HTMLInputElement).disabled).toBe(true);
  });

  it("does not enable memory or clarification when preset-controlled off", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride, patchConvPending: false },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    fireEvent.click(screen.getByTestId("chat-runtime-toggle-memoryEnabled"));
    fireEvent.click(screen.getByTestId("chat-runtime-toggle-clarificationEnabled"));

    expect(saveRuntimeOverride).not.toHaveBeenCalled();
  });

  it("does not enable ranker or post-retrieval when preset-controlled off", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride, patchConvPending: false },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    fireEvent.click(screen.getByTestId("chat-runtime-toggle-rankerEnabled"));
    fireEvent.click(screen.getByTestId("chat-runtime-toggle-postRetrievalEnabled"));

    expect(saveRuntimeOverride).not.toHaveBeenCalled();
  });

  it("does not enable function calling when preset-controlled off", () => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          stubCap("useRetrieval"),
          stubCap("toolsEnabled"),
          stubCap("functionCallingEnabled", [], ["naiveFullCorpusInPromptEnabled"]),
        ],
      },
      isLoading: false,
    });

    const saveRuntimeOverride = vi.fn();
    const state = baseRuntimeState();
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        saveRuntimeOverride,
        patchConvPending: false,
        runtimeState: state,
      },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    fireEvent.click(screen.getByTestId("chat-runtime-toggle-functionCallingEnabled"));

    expect(saveRuntimeOverride).not.toHaveBeenCalled();
  });

  it("disables useAdvisor when retrieval is off", () => {
    const state = baseRuntimeState();
    state.effectiveConfig = { ...state.effectiveConfig, useRetrieval: false };
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        patchConvPending: false,
        runtimeState: state,
      },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    const advisor = screen.getByTestId("chat-runtime-toggle-useAdvisor") as HTMLInputElement;
    expect(advisor.disabled).toBe(true);
  });

  it("shows preset initial flags from effective config", () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        patchConvPending: false,
        runtimeState: baseRuntimeState(),
      },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    expect(screen.getByTestId("chat-runtime-toggle-expansionEnabled")).not.toBeChecked();
    expect(screen.getByTestId("chat-runtime-toggle-toolsEnabled")).toBeChecked();
  });

  it("shows runtime override values from effective config", () => {
    const state = baseRuntimeState({ expansionEnabled: true, nerEnabled: true });
    state.effectiveConfig = {
      ...state.effectiveConfig,
      expansionEnabled: true,
      nerEnabled: true,
    };
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        patchConvPending: false,
        runtimeOverride: { expansionEnabled: true, nerEnabled: true },
        runtimeState: state,
      },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    expect(screen.getByTestId("chat-runtime-toggle-expansionEnabled")).toBeChecked();
    expect(screen.getByTestId("chat-runtime-toggle-nerEnabled")).toBeChecked();
  });
});
