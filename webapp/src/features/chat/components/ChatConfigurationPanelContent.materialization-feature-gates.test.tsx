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

const advancedCap = (key: string) => ({
  ...stubCap(key),
  category: "ADVANCED_RUNTIME" as const,
  supportMode: key === "memoryEnabled" || key === "clarificationEnabled" ? "MULTI_TURN_REQUIRED" : null,
});

function structuredSearchDisabled(key: string, reasonCode: string, reason: string): DisabledRuntimeFeatureDto {
  return { key, reasonCode, reason };
}

const STRUCTURED_SEARCH_DISABLED: DisabledRuntimeFeatureDto[] = [
  structuredSearchDisabled(
    "useRetrieval",
    "STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED",
    "Structured-search projects do not support vector retrieval.",
  ),
  structuredSearchDisabled(
    "naiveFullCorpusInPromptEnabled",
    "STRUCTURED_SEARCH_FULL_CONTEXT_UNSUPPORTED",
    "Full-context mode is unavailable because this project has no vector chunks.",
  ),
  structuredSearchDisabled(
    "useAdvisor",
    "STRUCTURED_SEARCH_ADVANCED_RETRIEVAL_UNSUPPORTED",
    "Advisor/ranker/post-retrieval require vector retrieval.",
  ),
  structuredSearchDisabled(
    "rankerEnabled",
    "STRUCTURED_SEARCH_ADVANCED_RETRIEVAL_UNSUPPORTED",
    "Advisor/ranker/post-retrieval require vector retrieval.",
  ),
  structuredSearchDisabled(
    "postRetrievalEnabled",
    "STRUCTURED_SEARCH_ADVANCED_RETRIEVAL_UNSUPPORTED",
    "Advisor/ranker/post-retrieval require vector retrieval.",
  ),
];

function baseRuntimeState(overrides: Record<string, unknown> = {}, disabled: DisabledRuntimeFeatureDto[] = []) {
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
    baseEffectiveConfig: { useRetrieval: false },
    effectiveConfig: {
      useRetrieval: false,
      expansionEnabled: false,
      nerEnabled: false,
      toolsEnabled: false,
      functionCallingEnabled: false,
      rankerEnabled: false,
      postRetrievalEnabled: false,
      naiveFullCorpusInPromptEnabled: false,
      useAdvisor: false,
      memoryEnabled: false,
      clarificationEnabled: false,
      judgeEnabled: false,
      reasoningEnabled: false,
      adaptiveRoutingEnabled: false,
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
    selectedWorkflow: "DirectLlmWorkflow",
    indexCompatibility: {
      activeProjectSnapshotId: "snap-1",
      activeConversationSnapshotId: null,
      activeIndexProfileHash: "hash-1",
      activeIndexProfile: { materializationStrategy: "STRUCTURED_SEARCH" },
      hasActiveIndex: true,
      activeSnapshotCapabilities: {
        materializationStrategy: "STRUCTURED_SEARCH",
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
    disabledRuntimeFeatures: disabled,
  };
}

function allCaps() {
  return [
    stubCap("useRetrieval"),
    stubCap("naiveFullCorpusInPromptEnabled", [], ["useRetrieval"]),
    stubCap("expansionEnabled"),
    stubCap("nerEnabled"),
    stubCap("toolsEnabled"),
    stubCap("functionCallingEnabled", [], ["naiveFullCorpusInPromptEnabled"]),
    stubCap("useAdvisor", ["useRetrieval"]),
    stubCap("rankerEnabled", ["useRetrieval"]),
    stubCap("postRetrievalEnabled", ["useRetrieval"]),
    advancedCap("memoryEnabled"),
    advancedCap("clarificationEnabled"),
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
}

function openEdit() {
  fireEvent.click(screen.getByTestId("chat-config-edit-button"));
}

describe("ChatConfigurationPanelContent materialization feature gates", () => {
  beforeEach(() => {
    hooksMock.useMeEffectiveEmbeddingDefaults.mockReturnValue({ data: null });
    hooksMock.useProjectIndexProfile.mockReturnValue({
      data: { materializationStrategy: "STRUCTURED_SEARCH", metadataEnabled: false },
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
        runtimeState: baseRuntimeState({}, STRUCTURED_SEARCH_DISABLED),
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

  const disabledKeys = [
    "useRetrieval",
    "naiveFullCorpusInPromptEnabled",
    "useAdvisor",
    "rankerEnabled",
    "postRetrievalEnabled",
  ] as const;

  const enabledKeys = [
    "expansionEnabled",
    "toolsEnabled",
    "functionCallingEnabled",
    "nerEnabled",
    "reasoningEnabled",
    "adaptiveRoutingEnabled",
    "judgeEnabled",
    "memoryEnabled",
    "clarificationEnabled",
  ] as const;

  it.each(disabledKeys)("STRUCTURED_SEARCH disables %s", (key) => {
    renderSubject();
    openEdit();
    const el = screen.getByTestId(`chat-runtime-toggle-${key}`) as HTMLInputElement;
    expect(el.disabled).toBe(true);
  });

  it.each(enabledKeys)("STRUCTURED_SEARCH allows %s", (key) => {
    renderSubject();
    openEdit();
    const el = screen.getByTestId(`chat-runtime-toggle-${key}`) as HTMLInputElement;
    expect(el.disabled).toBe(false);
  });

  it("shows query expansion toggle on STRUCTURED_SEARCH projects", () => {
    renderSubject();
    openEdit();
    expect(screen.getByTestId("chat-runtime-toggle-expansionEnabled")).toBeInTheDocument();
  });

  it("shows Tools label instead of Retrieval tools", () => {
    renderSubject();
    openEdit();
    expect(screen.getByText("Tools")).toBeInTheDocument();
    expect(screen.queryByText("Retrieval tools")).not.toBeInTheDocument();
  });

  it("HYBRID project allows ranker and post-retrieval together", () => {
    const state = baseRuntimeState({});
    state.effectiveConfig = {
      ...state.effectiveConfig,
      useRetrieval: true,
      rankerEnabled: false,
      postRetrievalEnabled: false,
    };
    state.indexCompatibility = {
      ...state.indexCompatibility!,
      activeSnapshotCapabilities: {
        materializationStrategy: "HYBRID",
        supportsMetadata: true,
        embeddingModelId: "emb",
        chunkMaxChars: 400,
        chunkOverlap: 40,
      },
    };
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, runtimeState: state },
    }));

    renderSubject();
    openEdit();
    expect((screen.getByTestId("chat-runtime-toggle-rankerEnabled") as HTMLInputElement).disabled).toBe(false);
    expect((screen.getByTestId("chat-runtime-toggle-postRetrievalEnabled") as HTMLInputElement).disabled).toBe(false);
  });

  it("memory and clarification can both be enabled without unsetting each other", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride, patchConvPending: false },
    }));
    renderSubject();
    openEdit();
    fireEvent.click(screen.getByTestId("chat-runtime-toggle-memoryEnabled"));
    fireEvent.click(screen.getByTestId("chat-runtime-toggle-clarificationEnabled"));
    expect(saveRuntimeOverride).toHaveBeenLastCalledWith({ clarificationEnabled: true });
  });

  it("FC excludes naive corpus when backend marks functionCalling disabled", () => {
    const disabled: DisabledRuntimeFeatureDto[] = [
      {
        key: "functionCallingEnabled",
        reasonCode: "EXCLUDES_naiveFullCorpusInPromptEnabled",
        reason: "Cannot be enabled with naiveFullCorpusInPromptEnabled=true.",
      },
    ];
    const state = baseRuntimeState({ naiveFullCorpusInPromptEnabled: true }, disabled);
    state.effectiveConfig = {
      ...state.effectiveConfig,
      naiveFullCorpusInPromptEnabled: true,
    };
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, runtimeState: state, runtimeOverride: { naiveFullCorpusInPromptEnabled: true } },
    }));
    renderSubject();
    openEdit();
    expect((screen.getByTestId("chat-runtime-toggle-functionCallingEnabled") as HTMLInputElement).disabled).toBe(true);
  });
});
