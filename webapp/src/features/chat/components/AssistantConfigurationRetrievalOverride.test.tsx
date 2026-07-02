import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
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

describe("AssistantConfigurationRetrievalOverride", () => {
  beforeEach(() => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null });
    hooksMock.useClassifierModelsQuery.mockReturnValue({ data: [], isError: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({ data: { capabilities: [] } });
    hooksMock.useMeEffectiveEmbeddingDefaults.mockReturnValue({
      data: { retrievalOptions: { topK: 8, similarityThreshold: 0.25, materializationStrategy: "CHUNK" } },
    });

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

  it("applies assistant retrieval defaults when selected", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    fireEvent.click(screen.getByTestId("chat-retrieval-mode-assistant-defaults"));

    expect(saveRuntimeOverride).toHaveBeenCalledWith({ topK: 8, similarityThreshold: 0.25 });
  });

  it("clears retrieval override keys for preset recommended mode", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        saveRuntimeOverride,
        runtimeState: {
          ...s.api!.runtimeState!,
          runtimeOverride: { topK: 8, similarityThreshold: 0.25 },
        },
      },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    fireEvent.click(screen.getByTestId("chat-retrieval-mode-preset"));

    expect(saveRuntimeOverride).toHaveBeenCalledWith({});
  });
});
