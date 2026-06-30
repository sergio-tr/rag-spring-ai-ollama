import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore, type ChatToolbarApi } from "@/features/chat/store/chat-toolbar.store";
import type { MeSelectableLlmModelDto } from "@/types/api";

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

const LEGACY_MODEL_IDS = ["gemma3:4b", "mistral:7b", "llama3.1:8b"] as const;

const apiModels: MeSelectableLlmModelDto[] = [
  {
    modelName: "gpt-oss:20b",
    displayName: "gpt-oss:20b",
    selectable: true,
    disabledReason: null,
    disabledReasonCode: null,
    usableAsDefault: true,
    runtimeStatus: "UNKNOWN",
  },
  {
    modelName: "ollama-missing",
    displayName: "ollama-missing",
    selectable: false,
    disabledReason: "Model not installed locally in Ollama",
    disabledReasonCode: "LLM_MODEL_UNAVAILABLE",
    usableAsDefault: false,
    runtimeStatus: "UNAVAILABLE",
  },
];

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
      effectiveConfig: { useRetrieval: true },
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
    selectableLlmModels: apiModels,
    selectableLlmModelsLoading: false,
    selectableLlmModelsEffectiveProvider: "OPENAI_COMPATIBLE" as const,
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

describe("ChatConfigurationPanelContent LLM model selector", () => {
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

  it("chat selector renders API models", async () => {
    useChatToolbarStore.setState({ api: baseToolbarApi() });
    await openModelSection();

    const select = screen.getByTestId("chat-llm-model-select");
    expect(select).toBeInTheDocument();
    expect(select).toHaveAttribute("data-effective-provider", "OPENAI_COMPATIBLE");
    expect(screen.getByTestId("chat-llm-model-provider")).toHaveTextContent(/Configured model provider/i);
    expect(screen.getByRole("option", { name: /gpt-oss:20b/i })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /ollama-missing \(unavailable\)/i })).toBeInTheDocument();
  });

  it("hardcoded legacy models do not appear", async () => {
    useChatToolbarStore.setState({ api: baseToolbarApi() });
    await openModelSection();

    const select = screen.getByTestId("chat-llm-model-select");
    const text = select.textContent ?? "";
    for (const legacy of LEGACY_MODEL_IDS) {
      expect(text).not.toContain(legacy);
    }
  });

  it("unavailable model is disabled", async () => {
    useChatToolbarStore.setState({ api: baseToolbarApi() });
    await openModelSection();

    const unavailable = screen.getByRole("option", { name: /ollama-missing \(unavailable\)/i });
    expect(unavailable).toBeDisabled();
    expect(screen.getByTestId("chat-llm-model-unavailable-hints").textContent).toMatch(/not installed/i);
  });

  it("invalid current selection shows warning", async () => {
    useChatToolbarStore.setState({
      api: baseToolbarApi({ llmModelChoice: "mistral:7b" }),
    });
    await openModelSection();

    expect(screen.getByTestId("chat-llm-model-selection-invalid")).toBeInTheDocument();
    expect(screen.getByTestId("chat-llm-model-selection-invalid").textContent).toMatch(/mistral:7b/i);
    expect(screen.getByTestId("chat-llm-model-invalid-option")).toBeInTheDocument();
  });

  it("API failure shows error", async () => {
    useChatToolbarStore.setState({
      api: baseToolbarApi({
        selectableLlmModels: [],
        modelsError: true,
        modelsErrorMessage: "Could not load models catalog.",
      }),
    });
    await openModelSection();

    expect(screen.getByTestId("chat-error-code-MODEL_UNAVAILABLE")).toHaveTextContent(/Could not load models catalog/i);
  });

  it("shows empty catalog message when API returns no models", async () => {
    useChatToolbarStore.setState({
      api: baseToolbarApi({ selectableLlmModels: [] }),
    });
    await openModelSection();

    expect(screen.getByTestId("chat-llm-model-catalog-empty")).toBeInTheDocument();
  });

  it("shows loading state while models are loading", async () => {
    useChatToolbarStore.setState({
      api: baseToolbarApi({ selectableLlmModels: [], selectableLlmModelsLoading: true }),
    });
    await openModelSection();

    expect(screen.getByTestId("chat-llm-models-loading")).toBeInTheDocument();
    expect(screen.getByTestId("chat-llm-model-select")).toBeDisabled();
  });
});
