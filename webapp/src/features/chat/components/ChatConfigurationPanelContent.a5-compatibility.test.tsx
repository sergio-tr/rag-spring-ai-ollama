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

const stubCap = (key: string, requires: string[] = [], excludes: string[] = []) => ({
  key,
  label: key,
  description: "",
  category: "RUNTIME_HOT_SWAPPABLE",
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

describe("ChatConfigurationPanelContent A5 feature compatibility", () => {
  beforeEach(() => {
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
          stubCap("rankerEnabled", ["useRetrieval"]),
          stubCap("postRetrievalEnabled", ["useRetrieval"]),
          stubCap("memoryEnabled"),
          stubCap("clarificationEnabled"),
        ],
      },
      isLoading: false,
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
            kind: "DEFAULT",
            code: null,
            label: "Recommended Default",
            chatSelectable: true,
            supported: true,
            supportStatus: null,
            reasonIfUnsupported: null,
          },
          baseEffectiveConfig: {
            useRetrieval: true,
            toolsEnabled: true,
          },
          effectiveConfig: {
            useRetrieval: true,
            expansionEnabled: false,
            nerEnabled: false,
            toolsEnabled: true,
            memoryEnabled: false,
            clarificationEnabled: false,
          },
          conversationLlmModel: null,
          conversationClassifierModelId: null,
          conversationModelsPinned: false,
          configurationMode: "PRESET" as const,
          runtimeOverride: {},
          manualOverrideKeys: [],
          isCustom: false,
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

  it("allows expansion and NER to both be enabled", async () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride },
    }));
    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    const expansion = screen.getByTestId("chat-runtime-toggle-expansionEnabled") as HTMLInputElement;
    const ner = screen.getByTestId("chat-runtime-toggle-nerEnabled") as HTMLInputElement;
    expect(expansion.disabled).toBe(false);
    expect(ner.disabled).toBe(false);
    fireEvent.click(expansion);
    fireEvent.click(ner);
    expect(saveRuntimeOverride).toHaveBeenNthCalledWith(1, { expansionEnabled: true });
    expect(saveRuntimeOverride).toHaveBeenNthCalledWith(2, { nerEnabled: true });
  });

  it("allows memory and clarification to both be enabled", async () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, saveRuntimeOverride, patchConvPending: false },
    }));
    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    const memory = screen.getByTestId("chat-runtime-toggle-memoryEnabled") as HTMLInputElement;
    const clarification = screen.getByTestId("chat-runtime-toggle-clarificationEnabled") as HTMLInputElement;
    expect(memory.disabled).toBe(false);
    expect(clarification.disabled).toBe(false);
    fireEvent.click(memory);
    fireEvent.click(clarification);
    expect(saveRuntimeOverride).toHaveBeenLastCalledWith({ clarificationEnabled: true });
  });

  it("shows HYBRID dependency hint for ranker on non-HYBRID index", async () => {
    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    const ranker = screen.getByTestId("chat-runtime-toggle-rankerEnabled") as HTMLInputElement;
    expect(ranker.disabled).toBe(false);
    expect(screen.getByTestId("chat-runtime-hybrid-hint-rankerEnabled")).toHaveTextContent(
      /only meaningful with HYBRID materialization/i,
    );
  });
});
