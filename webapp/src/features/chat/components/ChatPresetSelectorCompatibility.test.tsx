import { describe, expect, it, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { IntlTestProvider } from "@/test-utils/intl";
import { compatiblePresetsQueryMock } from "@/test-utils/compatible-presets-mock";
import { P3_PRESET_ID } from "@/features/chat/lib/preset-product-selection";

const hooksMock = vi.hoisted(() => ({
  useProjectIndexProfile: vi.fn(),
  useActiveProjectSnapshot: vi.fn(),
  useRuntimeConfigCapabilities: vi.fn(),
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

function renderSubject() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider>
        <ChatConfigurationPanelContent />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

describe("ChatPresetSelectorCompatibility", () => {
  beforeEach(() => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({ data: { capabilities: [] }, isLoading: false });
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null, isLoading: false, isError: false });

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
          baseEffectiveConfig: {},
          effectiveConfig: {},
          conversationLlmModel: null,
          conversationClassifierModelId: null,
          conversationModelsPinned: false,
          configurationMode: "PRESET" as const,
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
        projectCompatiblePresets: compatiblePresetsQueryMock.data,
        compatibleProductPresets: [
          {
            preset: { id: P3_PRESET_ID, name: "Chunk preset", system: true, description: null, tags: [], values: {}, createdAt: "", updatedAt: "" },
            indexRequirements: { requiredMaterializationStrategy: "CHUNK_LEVEL", requiresMetadataSupport: false },
            compatibility: {
              selectable: true,
              disabledReasonCode: null,
              disabledReason: null,
              indexRequirements: null,
              compatibleWithActiveIndex: true,
            },
          },
          {
            preset: { id: "hybrid-preset", name: "Hybrid preset", system: false, description: null, tags: [], values: {}, createdAt: "", updatedAt: "" },
            indexRequirements: { requiredMaterializationStrategy: "HYBRID", requiresMetadataSupport: true },
            compatibility: {
              selectable: false,
              disabledReasonCode: "MATERIALIZATION_NOT_SUPPORTED",
              disabledReason: "Requires HYBRID index",
              indexRequirements: { requiredMaterializationStrategy: "HYBRID", requiresMetadataSupport: true },
              compatibleWithActiveIndex: false,
            },
          },
        ],
        compatibleExperimentalPresets: [],
        experimentalPresets: [],
        experimentalPresetsLoading: false,
        experimentalPresetsError: false,
        presetSelectDisabled: false,
        syntheticPresetOptionNeeded: false,
        presetLabelOpts: { systemSuffix: "system", recommendedDefault: "Recommended", defaultConfiguration: "Default" },
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

  it("filters incompatible product presets by default", async () => {
    renderSubject();
    const user = userEvent.setup();
    await user.click(screen.getByTestId("chat-config-edit-button"));
    expect(screen.getByRole("option", { name: /Chunk preset/i })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Hybrid preset/i })).not.toBeInTheDocument();
  });

  it("does not render Show baseline presets toggle", async () => {
    renderSubject();
    const user = userEvent.setup();
    await user.click(screen.getByTestId("chat-config-edit-button"));
    expect(screen.queryByTestId("chat-preset-show-baseline")).not.toBeInTheDocument();
    expect(screen.queryByText(/Show baseline presets/i)).not.toBeInTheDocument();
  });

  it("shows incompatible presets disabled in advanced mode", async () => {
    renderSubject();
    const user = userEvent.setup();
    await user.click(screen.getByTestId("chat-config-edit-button"));
    await user.click(screen.getByTestId("chat-preset-show-incompatible"));
    const option = screen.getByRole("option", { name: /Hybrid preset/i }) as HTMLOptionElement;
    expect(option.disabled).toBe(true);
    expect(option.textContent).toMatch(/Requires HYBRID index/i);
  });
});
