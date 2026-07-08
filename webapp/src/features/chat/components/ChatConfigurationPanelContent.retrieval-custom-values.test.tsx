import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";

const hooksMock = vi.hoisted(() => ({
  useRuntimeConfigCapabilities: vi.fn(),
  useProjectIndexProfile: vi.fn(),
  useActiveProjectSnapshot: vi.fn(),
  useClassifierModelsQuery: vi.fn(),
  useMeEffectiveEmbeddingDefaults: vi.fn(),
  useProjectStoredRagConfigQuery: vi.fn(),
}));

vi.mock("@/features/chat/hooks/use-runtime-config-capabilities", () => ({
  useRuntimeConfigCapabilities: (...args: unknown[]) => hooksMock.useRuntimeConfigCapabilities(...args),
}));
vi.mock("@/features/projects/hooks/use-project-index-profile", () => ({
  useProjectIndexProfile: (...args: unknown[]) => hooksMock.useProjectIndexProfile(...args),
}));
vi.mock("@/features/projects/hooks/use-active-project-snapshot", () => ({
  useActiveProjectSnapshot: (...args: unknown[]) => hooksMock.useActiveProjectSnapshot(...args),
}));
vi.mock("@/features/lab/hooks/use-classifier-registry", () => ({
  useClassifierModelsQuery: (...args: unknown[]) => hooksMock.useClassifierModelsQuery(...args),
}));
vi.mock("@/features/settings/hooks/use-rag-config", () => ({
  useProjectStoredRagConfigQuery: (...args: unknown[]) => hooksMock.useProjectStoredRagConfigQuery(...args),
}));
vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", () => ({
  useMeEffectiveEmbeddingDefaults: (...args: unknown[]) => hooksMock.useMeEffectiveEmbeddingDefaults(...args),
}));

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

function openRetrievalSettings() {
  fireEvent.click(screen.getByTestId("chat-config-edit-button"));
}

describe("ChatConfigurationPanelContent retrieval custom values", () => {
  beforeEach(() => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null });
    hooksMock.useClassifierModelsQuery.mockReturnValue({ data: [], isError: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({ data: { capabilities: [] } });
    hooksMock.useMeEffectiveEmbeddingDefaults.mockReturnValue({
      data: { retrievalOptions: { topK: 8, similarityThreshold: 0.25, materializationStrategy: "CHUNK_LEVEL" } },
    });
    hooksMock.useProjectStoredRagConfigQuery.mockReturnValue({ data: null });

    useChatToolbarStore.setState({
      api: {
        projectId: "p1",
        conversationId: "c1",
        runtimeOverride: {},
        runtimeState: {
          conversationId: "c1",
          selectedPresetId: null,
          effectivePresetId: "preset",
          preset: {
            kind: "MISSING",
            code: null,
            label: "Assistant configuration",
            chatSelectable: false,
            supported: false,
            supportStatus: null,
            reasonIfUnsupported: null,
          },
          baseEffectiveConfig: { useRetrieval: true, topK: 5, similarityThreshold: 0.9 },
          effectiveConfig: { useRetrieval: true, topK: 5, similarityThreshold: 0.9 },
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
          selectedWorkflow: null,
          indexCompatibility: null,
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

  it("typing in topK after selecting custom mode commits values", async () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride },
    }));

    renderSubject();
    openRetrievalSettings();

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-custom"));
    saveRuntimeOverride.mockClear();

    const topK = screen.getByTestId("chat-runtime-toggle-topK");
    fireEvent.change(topK, { target: { value: "7" } });

    expect(screen.getByTestId("chat-retrieval-mode-custom")).toBeChecked();
    expect(saveRuntimeOverride).toHaveBeenCalledWith(
      expect.objectContaining({
        retrievalOverrideMode: "custom",
        topK: 7,
        similarityThreshold: 0.9,
      }),
    );
  });

  it("typing in similarityThreshold after selecting custom mode commits values", async () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride },
    }));

    renderSubject();
    openRetrievalSettings();

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-custom"));
    saveRuntimeOverride.mockClear();

    const threshold = screen.getByTestId("chat-runtime-toggle-similarityThreshold");
    fireEvent.change(threshold, { target: { value: "0.55" } });

    expect(screen.getByTestId("chat-retrieval-mode-custom")).toBeChecked();
    expect(saveRuntimeOverride).toHaveBeenCalledWith(
      expect.objectContaining({
        retrievalOverrideMode: "custom",
        topK: 5,
        similarityThreshold: 0.55,
      }),
    );
  });

  it("clearing topK input does not immediately restore previous value", async () => {
    renderSubject();
    openRetrievalSettings();

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-custom"));

    const topK = screen.getByTestId("chat-runtime-toggle-topK") as HTMLInputElement;
    fireEvent.change(topK, { target: { value: "" } });

    expect(topK.value).toBe("");
  });

  it("typing 0.55 in similarityThreshold is possible", async () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride },
    }));

    renderSubject();
    openRetrievalSettings();

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-custom"));

    const threshold = screen.getByTestId("chat-runtime-toggle-similarityThreshold") as HTMLInputElement;
    const user = userEvent.setup();
    await user.clear(threshold);
    await user.type(threshold, "0.55");

    expect(threshold.value).toBe("0.55");
    expect(saveRuntimeOverride).toHaveBeenLastCalledWith(
      expect.objectContaining({
        retrievalOverrideMode: "custom",
        similarityThreshold: 0.55,
      }),
    );
  });

  it("invalid topK shows error and does not PATCH invalid override", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride },
    }));

    renderSubject();
    openRetrievalSettings();

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-custom"));

    const topK = screen.getByTestId("chat-runtime-toggle-topK");
    saveRuntimeOverride.mockClear();
    fireEvent.change(topK, { target: { value: "abc" } });

    expect(screen.getByTestId("chat-runtime-toggle-topK-error")).toBeInTheDocument();
    expect(saveRuntimeOverride).not.toHaveBeenCalled();
  });

  it("invalid threshold shows error and does not PATCH invalid override", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride },
    }));

    renderSubject();
    openRetrievalSettings();

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-custom"));

    const threshold = screen.getByTestId("chat-runtime-toggle-similarityThreshold");
    saveRuntimeOverride.mockClear();
    fireEvent.change(threshold, { target: { value: "1.5" } });

    expect(screen.getByTestId("chat-runtime-toggle-similarityThreshold-error")).toBeInTheDocument();
    expect(saveRuntimeOverride).not.toHaveBeenCalled();
  });

  it("valid topK PATCHes runtimeOverride", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride },
    }));

    renderSubject();
    openRetrievalSettings();

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-custom"));

    const topK = screen.getByTestId("chat-runtime-toggle-topK");
    fireEvent.change(topK, { target: { value: "12" } });

    expect(saveRuntimeOverride).toHaveBeenCalledWith(
      expect.objectContaining({
        retrievalOverrideMode: "custom",
        topK: 12,
      }),
    );
  });

  it("valid threshold PATCHes runtimeOverride", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride },
    }));

    renderSubject();
    openRetrievalSettings();

    const threshold = screen.getByTestId("chat-runtime-toggle-similarityThreshold");
    fireEvent.change(threshold, { target: { value: "0.4" } });

    expect(saveRuntimeOverride).toHaveBeenCalledWith(
      expect.objectContaining({
        retrievalOverrideMode: "custom",
        similarityThreshold: 0.4,
      }),
    );
  });

  it("reset clears custom retrieval overrides", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState(() => ({
      api: {
        ...useChatToolbarStore.getState().api!,
        saveRuntimeOverride,
        runtimeState: {
          ...useChatToolbarStore.getState().api!.runtimeState!,
          runtimeOverride: {
            retrievalOverrideMode: "custom",
            topK: 12,
            similarityThreshold: 0.4,
          },
        },
      },
    }));

    renderSubject();
    openRetrievalSettings();
    fireEvent.click(screen.getByTestId("chat-retrieval-mode-preset"));

    expect(saveRuntimeOverride).toHaveBeenCalledWith({ retrievalOverrideMode: "preset" });
  });
});
