import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore, type ChatToolbarApi } from "@/features/chat/store/chat-toolbar.store";

const hooksMock = vi.hoisted(() => ({
  useRuntimeConfigCapabilities: vi.fn(),
  useProjectIndexProfile: vi.fn(),
  useActiveProjectSnapshot: vi.fn(),
  useClassifierModelsQuery: vi.fn(),
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
      baseEffectiveConfig: { useRetrieval: true },
      effectiveConfig: { useRetrieval: true, llmModel: "gpt-oss:20b" },
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
    selectableLlmModelsEffectiveProvider: "OPENAI_COMPATIBLE" as const,
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
    ...overrides,
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

describe("ChatConfigurationPanelContent LLM models (Assistant Configuration)", () => {
  beforeEach(() => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useClassifierModelsQuery.mockReturnValue({ data: [], isError: false, isLoading: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: { capabilities: [] },
      isLoading: false,
    });
  });

  async function openModelSection() {
    renderSubject();
    const user = userEvent.setup();
    await user.click(screen.getByTestId("chat-config-edit-button"));
  }

  it("links to Assistant Configuration instead of a generic LLM selector", async () => {
    useChatToolbarStore.setState({ api: baseToolbarApi() });
    await openModelSection();

    expect(screen.queryByTestId("chat-llm-model-select")).not.toBeInTheDocument();
    expect(await screen.findByRole("link", { name: /Open Assistant Configuration/i })).toBeInTheDocument();
    expect(await screen.findByTestId("chat-llm-configuration-hint")).toHaveTextContent(/Assistant Configuration/i);
  });

  it("shows optional final-answer override with scoped label in edit section", async () => {
    useChatToolbarStore.setState({
      api: baseToolbarApi({
        selectableLlmModels: [
          {
            modelName: "gpt-oss:20b",
            displayName: "GPT OSS 20B",
            selectable: true,
            disabledReason: null,
            disabledReasonCode: null,
            usableAsDefault: true,
            runtimeStatus: "NOT_PROBED",
          },
        ],
      }),
    });
    await openModelSection();

    expect(screen.getByTestId("chat-final-answer-model-select")).toBeInTheDocument();
    expect(screen.getByText(/This optional override only pins the final answer model for this conversation/i)).toBeInTheDocument();
    expect(screen.getByText(/role-based models from Assistant Configuration/i)).toBeInTheDocument();
  });

  it("shows effective model label in summary row", async () => {
    useChatToolbarStore.setState({
      api: baseToolbarApi({ llmModelChoice: "gpt-oss:20b" }),
    });
    await openModelSection();

    expect(screen.getByTestId("chat-llm-configuration-hint")).toBeInTheDocument();
  });
});
