import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { IntlTestProvider } from "@/test-utils/intl";

const stubCap = (key: string, requires: string[] = []) => ({
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
  excludes: [] as string[],
  requiresIndexSnapshot: false,
  requiresReindexWhenChanged: false,
  reasonIfDisabled: null as string | null,
  reasonIfNotImplemented: null as string | null,
});

vi.mock("@/features/projects/hooks/use-project-index-profile", () => ({
  useProjectIndexProfile: () => ({
    data: {
      projectId: "p1",
      materializationStrategy: "FULL_TEXT",
      metadataEnabled: false,
      metadataProfile: null,
      embeddingModelId: "m1",
      chunkMaxChars: 800,
      chunkOverlap: 80,
      profileHash: "h",
      createdAt: "2020-01-01T00:00:00Z",
      updatedAt: "2020-01-01T00:00:00Z",
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/projects/hooks/use-active-project-snapshot", () => ({
  useActiveProjectSnapshot: () => ({
    data: null,
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/chat/hooks/use-runtime-config-capabilities", () => ({
  useRuntimeConfigCapabilities: () => ({
    data: {
      capabilities: [
        stubCap("useRetrieval"),
        stubCap("naiveFullCorpusInPromptEnabled"),
        stubCap("useAdvisor", ["useRetrieval"]),
        stubCap("reasoningEnabled"),
        stubCap("rankerEnabled", ["useRetrieval"]),
        stubCap("postRetrievalEnabled", ["useRetrieval"]),
        stubCap("clarificationEnabled"),
        stubCap("memoryEnabled"),
        stubCap("adaptiveRoutingEnabled"),
        stubCap("judgeEnabled"),
      ],
    },
    isLoading: false,
  }),
}));
vi.mock("@/features/lab/hooks/use-classifier-registry", () => ({
  useClassifierModelsQuery: () => ({ data: [], isError: false, isLoading: false }),
}));

describe("ChatConfigurationPanelContent retrieval dependency", () => {
  beforeEach(() => {
    useChatToolbarStore.setState({
      api: {
        projectId: "p1",
        conversationId: "c1",
        runtimeOverride: {},
        runtimeState: {
          conversationId: "c1",
          selectedPresetId: null,
          effectivePresetId: "cafe0001-0001-4001-8001-000000000003",
          preset: {
            kind: "DEFAULT",
            code: null,
            label: "Recommended Default",
            chatSelectable: true,
            supported: true,
            supportStatus: null,
            reasonIfUnsupported: null,
          },
          baseEffectiveConfig: { useRetrieval: false, rankerEnabled: false },
          effectiveConfig: { useRetrieval: false, rankerEnabled: false },
          runtimeOverride: {},
          manualOverrideKeys: [],
          isCustom: false,
          validation: { valid: true, supported: true, errors: [], warnings: [] },
          selectedWorkflow: "dense",
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
        modelsCatalog: undefined,
        modelsError: false,
        modelsErrorMessage: "",
        presetSelectValue: "",
        onPresetChange: vi.fn(),
        presets: [],
        presetsError: false,
        presetsLoading: false,
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
      },
    });
  });

  it("disables ranker toggle when useRetrieval is false and shows dependency hint", async () => {
    const user = userEvent.setup();
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider>
          <ChatConfigurationPanelContent />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await user.click(screen.getByTestId("chat-config-runtime-collapsible"));

    const ranker = screen.getByRole("checkbox", { name: /ranker/i });
    expect(ranker).toBeDisabled();
    const rankerRow = ranker.closest(".flex.flex-col");
    expect(rankerRow).not.toBeNull();
    await user.click(within(rankerRow as HTMLElement).getByRole("button", { name: /^Disabled$/i }));
    expect(await screen.findByText(/Requires useRetrieval/i)).toBeInTheDocument();
  });
});
