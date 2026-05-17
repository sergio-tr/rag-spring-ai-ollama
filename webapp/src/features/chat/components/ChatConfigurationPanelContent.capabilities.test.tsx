import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";

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

describe("ChatConfigurationPanelContent runtime capability toggles", () => {
  beforeEach(() => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useClassifierModelsQuery.mockReturnValue({ data: [], isError: false, isLoading: false });

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

  it("exposes stable chat configuration controls for E2E flows", () => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: { capabilities: [] },
      isLoading: false,
    });
    hooksMock.useClassifierModelsQuery.mockReturnValue({
      data: [{ id: "clf-1", name: "Default classifier", inferenceTag: "default", status: "READY", active: true }],
      isError: false,
      isLoading: false,
    });

    renderSubject();

    expect(screen.getByTestId("chat-limit-documents-checkbox")).toBeInTheDocument();
    expect(screen.getByTestId("chat-open-documents-sheet")).toBeInTheDocument();
    expect(screen.getByTestId("chat-preset-select")).toBeInTheDocument();
    expect(screen.getByTestId("chat-llm-model-select")).toBeInTheDocument();
    expect(screen.getByTestId("chat-classifier-select")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /Default classifier \(default\)/i })).toBeInTheDocument();
  });

  it("saves topK and similarityThreshold as runtime overrides", () => {
    const saveRuntimeOverride = vi.fn();
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: { capabilities: [] },
      isLoading: false,
    });
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        saveRuntimeOverride,
        runtimeState: {
          ...s.api!.runtimeState!,
          effectiveConfig: { useRetrieval: true, topK: 7, similarityThreshold: 0.25 },
        },
      },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-runtime-collapsible"));
    fireEvent.change(screen.getByTestId("chat-runtime-toggle-topK"), { target: { value: "9" } });
    fireEvent.change(screen.getByTestId("chat-runtime-toggle-similarityThreshold"), { target: { value: "0.4" } });

    expect(saveRuntimeOverride).toHaveBeenCalledWith({ topK: 9 });
    expect(saveRuntimeOverride).toHaveBeenCalledWith({ similarityThreshold: 0.4 });
  });

  it("renders classifier loading errors without hiding document scope controls", () => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: { capabilities: [] },
      isLoading: false,
    });
    hooksMock.useClassifierModelsQuery.mockReturnValue({ data: [], isError: true, isLoading: false });

    renderSubject();

    expect(screen.getByTestId("chat-open-documents-sheet")).toBeEnabled();
    expect(screen.getByText(/Could not load classifier models/i)).toBeInTheDocument();
  });

  it("opens advanced configuration and disables toggle when capability has reasonIfDisabled", () => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          {
            key: "toolsEnabled",
            label: "Tools",
            description: "d",
            category: "RUNTIME_HOT_SWAPPABLE",
            visibleInChat: true,
            configurableInChat: true,
            implemented: true,
            engineWired: true,
            supportMode: null,
            displayOrder: 1,
            requires: [],
            excludes: [],
            requiresIndexSnapshot: false,
            requiresReindexWhenChanged: false,
            reasonIfDisabled: "Policy disabled",
            reasonIfNotImplemented: null,
          },
        ],
      },
      isLoading: false,
    });

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-runtime-collapsible"));
    const row = screen.getByText("Tools").closest("div");
    expect(row).toBeTruthy();
    const checkbox = within(row as HTMLElement).getByRole("checkbox") as HTMLInputElement;
    expect(checkbox.disabled).toBe(true);
    // DisabledReason popover trigger should exist.
    expect(screen.getByText("Disabled")).toBeInTheDocument();
  });

  it("keeps valid runtime features editable", () => {
    const saveRuntimeOverride = vi.fn();
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          {
            key: "reasoningEnabled",
            label: "Reasoning",
            description: "d",
            category: "RUNTIME_HOT_SWAPPABLE",
            visibleInChat: true,
            configurableInChat: true,
            implemented: true,
            engineWired: true,
            supportMode: null,
            displayOrder: 1,
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
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        saveRuntimeOverride,
        runtimeState: { ...s.api!.runtimeState!, effectiveConfig: { reasoningEnabled: false } },
      },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-runtime-collapsible"));
    const row = screen.getByText("Reasoning").closest("div");
    const checkbox = within(row as HTMLElement).getByRole("checkbox") as HTMLInputElement;
    expect(checkbox.disabled).toBe(false);
    fireEvent.click(checkbox);
    expect(saveRuntimeOverride).toHaveBeenCalledWith({ reasoningEnabled: true });
  });

  it("hides runtime toggles that are not configurable in chat (filtered out)", () => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          {
            key: "rankerEnabled",
            label: "Ranker",
            description: "d",
            category: "RUNTIME_HOT_SWAPPABLE",
            visibleInChat: true,
            configurableInChat: false,
            implemented: true,
            engineWired: true,
            supportMode: null,
            displayOrder: 1,
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

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-runtime-collapsible"));
    expect(screen.queryByText("Ranker")).toBeNull();
  });

  it("renders not implemented runtime toggles disabled with reason", () => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          {
            key: "nerEnabled",
            label: "NER",
            description: "d",
            category: "RUNTIME_HOT_SWAPPABLE",
            visibleInChat: true,
            configurableInChat: true,
            implemented: false,
            engineWired: true,
            supportMode: null,
            displayOrder: 1,
            requires: [],
            excludes: [],
            requiresIndexSnapshot: false,
            requiresReindexWhenChanged: false,
            reasonIfDisabled: null,
            reasonIfNotImplemented: "Not wired",
          },
        ],
      },
      isLoading: false,
    });

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-runtime-collapsible"));
    const row = screen.getByText("NER").closest("div");
    const checkbox = within(row as HTMLElement).getByRole("checkbox") as HTMLInputElement;
    expect(checkbox.disabled).toBe(true);
    expect(screen.getByText("Disabled")).toBeInTheDocument();
  });

  it("disables toggle when capability requires another flag that is false in effective config", () => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          {
            key: "postRetrievalEnabled",
            label: "Post retrieval",
            description: "d",
            category: "RUNTIME_HOT_SWAPPABLE",
            visibleInChat: true,
            configurableInChat: true,
            implemented: true,
            engineWired: true,
            supportMode: null,
            displayOrder: 1,
            requires: ["useRetrieval"],
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

    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...s.api!.runtimeState!,
          effectiveConfig: { useRetrieval: false },
        },
      },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-runtime-collapsible"));
    const row = screen.getByText("Post retrieval").closest("div");
    const checkbox = within(row as HTMLElement).getByRole("checkbox") as HTMLInputElement;
    expect(checkbox.disabled).toBe(true);
    expect(screen.getByText("Disabled")).toBeInTheDocument();
  });

  it("uses backend disabledRuntimeFeatures reason before local capability rules", () => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          {
            key: "postRetrievalEnabled",
            label: "Post retrieval",
            description: "d",
            category: "RUNTIME_HOT_SWAPPABLE",
            visibleInChat: true,
            configurableInChat: true,
            implemented: true,
            engineWired: true,
            supportMode: null,
            displayOrder: 1,
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
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...s.api!.runtimeState!,
          disabledRuntimeFeatures: [
            {
              key: "postRetrievalEnabled",
              reasonCode: "REQUIRES_useRetrieval",
              reason: "Requires retrieval from backend contract.",
            },
          ],
        },
      },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-runtime-collapsible"));
    const row = screen.getByText("Post retrieval").closest("div");
    const checkbox = within(row as HTMLElement).getByRole("checkbox") as HTMLInputElement;
    expect(checkbox.disabled).toBe(true);
    expect(screen.getByText("Disabled")).toBeInTheDocument();
  });

  it("disables toggle when capability excludes another flag that is true in override", () => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          {
            key: "toolsEnabled",
            label: "Tools",
            description: "d",
            category: "RUNTIME_HOT_SWAPPABLE",
            visibleInChat: true,
            configurableInChat: true,
            implemented: true,
            engineWired: true,
            supportMode: null,
            displayOrder: 1,
            requires: [],
            excludes: ["useRetrieval"],
            requiresIndexSnapshot: false,
            requiresReindexWhenChanged: false,
            reasonIfDisabled: null,
            reasonIfNotImplemented: null,
          },
        ],
      },
      isLoading: false,
    });

    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeOverride: { useRetrieval: true },
        // Effective config already includes runtimeOverride values.
        runtimeState: { ...s.api!.runtimeState!, effectiveConfig: { useRetrieval: true } },
      },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-runtime-collapsible"));
    const row = screen.getByText("Tools").closest("div");
    const checkbox = within(row as HTMLElement).getByRole("checkbox") as HTMLInputElement;
    expect(checkbox.disabled).toBe(true);
    expect(screen.getByText("Disabled")).toBeInTheDocument();
  });

  it("treats string 'true' as truthy for excludes checks (coerceBool branch)", () => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          {
            key: "toolsEnabled",
            label: "Tools",
            description: "d",
            category: "RUNTIME_HOT_SWAPPABLE",
            visibleInChat: true,
            configurableInChat: true,
            implemented: true,
            engineWired: true,
            supportMode: null,
            displayOrder: 1,
            requires: [],
            excludes: ["useRetrieval"],
            requiresIndexSnapshot: false,
            requiresReindexWhenChanged: false,
            reasonIfDisabled: null,
            reasonIfNotImplemented: null,
          },
        ],
      },
      isLoading: false,
    });

    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeOverride: { useRetrieval: "true" },
        // Effective config already includes runtimeOverride values.
        runtimeState: { ...s.api!.runtimeState!, effectiveConfig: { useRetrieval: "true" } },
      },
    }));

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-runtime-collapsible"));
    const row = screen.getByText("Tools").closest("div");
    const checkbox = within(row as HTMLElement).getByRole("checkbox") as HTMLInputElement;
    expect(checkbox.disabled).toBe(true);
  });

  it("shows multi-turn badge and hint when capability supportMode is MULTI_TURN_REQUIRED", () => {
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
        ],
      },
      isLoading: false,
    });

    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-runtime-collapsible"));
    expect(screen.getByText("Multi-turn")).toBeInTheDocument();
    expect(screen.getByText("May use multiple turns.")).toBeInTheDocument();
  });
});

