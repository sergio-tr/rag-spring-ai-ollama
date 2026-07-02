import { describe, expect, it, beforeEach, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
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

function runtimeCap(key: string, label: string, displayOrder = 1) {
  return {
    key,
    label,
    description: "d",
    category: "RUNTIME_HOT_SWAPPABLE" as const,
    visibleInChat: true,
    configurableInChat: true,
    implemented: true,
    engineWired: true,
    supportMode: null,
    displayOrder,
    requires: [] as string[],
    excludes: [] as string[],
    requiresIndexSnapshot: false,
    requiresReindexWhenChanged: false,
    reasonIfDisabled: null,
    reasonIfNotImplemented: null,
  };
}

function indexBoundCap(key: string, label: string) {
  return {
    key,
    label,
    description: "d",
    category: "INDEX_BOUND" as const,
    visibleInChat: true,
    configurableInChat: false,
    implemented: true,
    engineWired: true,
    supportMode: null,
    displayOrder: 1,
    requires: [] as string[],
    excludes: [] as string[],
    requiresIndexSnapshot: true,
    requiresReindexWhenChanged: true,
    reasonIfDisabled: "Index snapshot compatibility; changing requires reindex.",
    reasonIfNotImplemented: null,
  };
}

function seedStore(overrides: Record<string, unknown> = {}) {
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
        baseEffectiveConfig: { useRetrieval: true },
        effectiveConfig: {
          useRetrieval: true,
          topK: 5,
          similarityThreshold: 0.7,
          memoryEnabled: true,
          clarificationEnabled: false,
          judgeEnabled: true,
          llmSystemPrompt: "You are a helpful assistant for municipal records.",
          embeddingModel: "nomic-embed-text",
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
      llmModelChoice: "llama3.2",
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
      documents: [],
      ...overrides,
    },
  });
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

describe("Assistant configuration UI sections", () => {
  beforeEach(() => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useClassifierModelsQuery.mockReturnValue({ data: [], isError: false, isLoading: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          runtimeCap("useRetrieval", "Use retrieval"),
          runtimeCap("memoryEnabled", "Memory", 2),
          runtimeCap("clarificationEnabled", "Clarification", 3),
          runtimeCap("judgeEnabled", "Judge", 4),
          indexBoundCap("embeddingModel", "Embedding model"),
        ],
      },
      isLoading: false,
    });
    seedStore();
  });

  it("exposes required product section headings when edit mode is open", async () => {
    const user = userEvent.setup();
    renderSubject();
    await user.click(screen.getByTestId("chat-config-edit-button"));

    expect(screen.getByTestId("chat-assistant-configuration-surface")).toBeInTheDocument();

    const required = [
      "Assistant",
      "Models",
      "Retrieval",
      "Prompts",
      "Memory and clarification",
      "Tools and quality checks",
    ];
    for (const title of required) {
      expect(screen.getByRole("heading", { name: title })).toBeInTheDocument();
    }
  });

  it("hides raw effective JSON until Advanced technical details is expanded", async () => {
    const user = userEvent.setup();
    renderSubject();
    const jsonNode = screen.queryByTestId("chat-config-effective-json");
    if (jsonNode) {
      expect(jsonNode).not.toBeVisible();
    }

    const advanced = screen.getByTestId("chat-config-advanced-technical");
    const jsonAfterClosed = screen.queryByTestId("chat-config-effective-json");
    if (jsonAfterClosed) {
      expect(jsonAfterClosed).not.toBeVisible();
    }

    await user.click(within(advanced).getByText(/Advanced technical details/i));
    expect(screen.getByTestId("chat-config-effective-json")).toBeVisible();
  });

  it("does not render evaluation preset latency warning for research presets", async () => {
    const user = userEvent.setup();
    const experimentalPreset = {
      productPresetId: "exp-p12",
      code: "P12",
      protocolStageIndex: 12,
      chatSelectable: true,
      supportStatus: "EXECUTABLE",
      reasonIfUnsupported: null,
      indexRequirements: null,
    };
    seedStore({
      runtimeState: {
        conversationId: "c1",
        selectedPresetId: "exp-p12",
        effectivePresetId: "exp-p12",
        preset: {
          kind: "EXPERIMENTAL",
          code: "P12",
          label: "Research preset",
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
      compatibleExperimentalPresets: [
        {
          preset: experimentalPreset,
          indexRequirements: null,
          compatibility: {
            selectable: true,
            disabledReasonCode: null,
            disabledReason: null,
            indexRequirements: null,
            compatibleWithActiveIndex: true,
          },
        },
      ],
      experimentalPresets: [experimentalPreset],
    });
    renderSubject();
    await user.click(screen.getByTestId("chat-config-edit-button"));

    expect(screen.queryByTestId("chat-preset-latency-warning")).not.toBeInTheDocument();
    expect(screen.queryByText(/70 seconds or more/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/recommended production configuration/i)).not.toBeInTheDocument();
    expect(screen.getByTestId("chat-preset-select")).toBeInTheDocument();
  });

  it("does not show Demo_ preset names in the configuration profile select", async () => {
    const user = userEvent.setup();
    const preset = {
      id: "pr-best",
      name: "demo_best",
      description: null,
      tags: [],
      values: {},
      system: true,
      createdAt: "",
      updatedAt: "",
    };
    seedStore({
      presets: [preset],
      compatibleProductPresets: [
        {
          preset,
          indexRequirements: null,
          compatibility: {
            selectable: true,
            disabledReasonCode: null,
            disabledReason: null,
            indexRequirements: null,
            compatibleWithActiveIndex: true,
          },
        },
      ],
    });
    renderSubject();
    await user.click(screen.getByTestId("chat-config-edit-button"));
    const select = screen.getByTestId("chat-preset-select");
    expect(select.textContent ?? "").not.toMatch(/demo_best/i);
    expect(select.textContent ?? "").toMatch(/Production assistant configuration/);
  });
});
