import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { CompactSummaryRow } from "./chat-config-compact-ui";
import { ChatConfigurationSidePanel } from "./ChatConfigurationSidePanel";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";

const hooksMock = vi.hoisted(() => ({
  useRuntimeConfigCapabilities: vi.fn(),
  useProjectIndexProfile: vi.fn(),
  useActiveProjectSnapshot: vi.fn(),
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
  useClassifierModelsQuery: () => ({ data: [], isError: false, isLoading: false }),
}));
vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", () => ({
  useMeEffectiveEmbeddingDefaults: (...args: unknown[]) => hooksMock.useMeEffectiveEmbeddingDefaults(...args),
}));

const LONG_MODEL =
  "org.example.very-long-provider-prefix/gpt-super-long-model-name-with-version-and-quantization-suffix";

function renderPanelContent() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider locale="en">
        <ChatConfigurationPanelContent />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

function renderSidePanel() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider locale="en">
        <ChatConfigurationSidePanel open onClose={() => undefined} />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

describe("chat config layout", () => {
  beforeEach(() => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: { capabilities: [] },
      isLoading: false,
    });
    hooksMock.useMeEffectiveEmbeddingDefaults.mockReturnValue({
      data: { retrievalOptions: { topK: 8, similarityThreshold: 0.25, materializationStrategy: "CHUNK_LEVEL" } },
    });
  });

  it("compact summary row wraps long values", () => {
    render(
      <CompactSummaryRow
        label="Model"
        testId="layout-summary-row"
        value={<span data-testid="layout-long-value">{LONG_MODEL}</span>}
      />,
    );

    const row = screen.getByTestId("layout-summary-row");
    expect(row.className).toMatch(/min-w-0/);
    const valueWrap = row.querySelector("span.min-w-0");
    expect(valueWrap?.className).toMatch(/break-words/);
    expect(valueWrap?.className).toMatch(/overflow-wrap:anywhere/);
    expect(screen.getByTestId("layout-long-value")).toHaveTextContent(LONG_MODEL);
  });

  it("config side panel constrains width and allows overflow wrap", () => {
    renderSidePanel();

    const panel = screen.getByTestId("chat-configuration-side-panel");
    expect(panel.className).toMatch(/min-w-0/);
    expect(panel.className).toMatch(/max-w-/);
    const scroll = panel.querySelector(".overflow-y-auto");
    expect(scroll?.className).toMatch(/min-w-0/);
    expect(scroll?.className).toMatch(/overflow-x-hidden/);
  });

  it("long model names do not overflow in compact summary", () => {
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
          effectiveConfig: { useRetrieval: true },
          conversationLlmModel: LONG_MODEL,
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

    renderPanelContent();

    const modelSummary = screen.getByTestId("chat-config-summary-model");
    expect(modelSummary).toHaveTextContent(LONG_MODEL);
    expect(modelSummary.className).toMatch(/break-words/);
  });

  it("selector shows provider-aware models from toolbar API", async () => {
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
          effectiveConfig: { useRetrieval: true },
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
        selectableLlmModels: [
          {
            modelName: "gpt-oss:20b",
            displayName: "gpt-oss:20b",
            selectable: true,
            disabledReason: null,
            disabledReasonCode: null,
            usableAsDefault: true,
            runtimeStatus: "UNKNOWN",
          },
        ],
        selectableLlmModelsLoading: false,
        selectableLlmModelsEffectiveProvider: "OPENAI_COMPATIBLE",
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

    renderPanelContent();
    const user = userEvent.setup();
    await user.click(screen.getByTestId("chat-config-edit-button"));

    expect(screen.getByTestId("chat-edit-assistant-configuration-link")).toBeInTheDocument();
  });

  it("uses adaptive flex-wrap for retrieval parameters and feature badges", async () => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          {
            key: "memoryEnabled",
            label: "Memory",
            description: "d",
            category: "RUNTIME_HOT_SWAPPABLE",
            visibleInChat: true,
            configurableInChat: true,
            implemented: true,
            engineWired: true,
            supportMode: "MULTI_TURN_REQUIRED",
            displayOrder: 1,
            requires: [],
            excludes: [],
            requiresIndexSnapshot: false,
            requiresReindexWhenChanged: false,
            reasonIfDisabled: null,
            reasonIfNotImplemented: null,
          },
          {
            key: "clarificationEnabled",
            label: "Clarification",
            description: "d",
            category: "RUNTIME_HOT_SWAPPABLE",
            visibleInChat: true,
            configurableInChat: true,
            implemented: true,
            engineWired: true,
            supportMode: "MULTI_TURN_REQUIRED",
            displayOrder: 2,
            requires: [],
            excludes: [],
            requiresIndexSnapshot: false,
            requiresReindexWhenChanged: false,
            reasonIfDisabled: null,
            reasonIfNotImplemented: null,
          },
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
          baseEffectiveConfig: { useRetrieval: true },
          effectiveConfig: { useRetrieval: true },
          effectiveRetrievalParameters: {
            topK: 8,
            similarityThreshold: 0.25,
            topKSource: "PROJECT_DEFAULTS",
            similarityThresholdSource: "PROJECT_DEFAULTS",
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

    renderPanelContent();
    const user = userEvent.setup();
    await user.click(screen.getByTestId("chat-config-edit-button"));

    const retrievalSection = screen.getByTestId("chat-retrieval-settings-section");
    const retrievalRow = retrievalSection.querySelector(".flex.flex-wrap");
    expect(retrievalRow?.className).toMatch(/flex-wrap/);
    expect(screen.getByTestId("chat-runtime-toggle-topK")).toBeInTheDocument();
    expect(screen.getByTestId("chat-runtime-toggle-similarityThreshold")).toBeInTheDocument();

    const memorySection = screen.getByTestId("chat-conversation-memory-section");
    const clarificationSection = screen.getByTestId("chat-clarification-section");
    expect(memorySection.querySelector(".flex-wrap")).toBeTruthy();
    expect(clarificationSection.querySelector(".flex-wrap")).toBeTruthy();
    expect(screen.getByText("Memory and clarification")).toBeInTheDocument();
    expect(screen.getAllByText("Multi-turn").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("Project setting").length).toBeGreaterThanOrEqual(2);
  });
});
