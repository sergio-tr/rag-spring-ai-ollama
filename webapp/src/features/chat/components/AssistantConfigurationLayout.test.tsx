import { describe, expect, it, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import type { ExperimentalPresetCatalogItemDto } from "@/types/api";

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

const LONG_MODEL =
  "org.example.very-long-provider-prefix/gpt-super-long-model-name-with-version-and-quantization-suffix";

const experimentalP14Preset: ExperimentalPresetCatalogItemDto = {
  productPresetId: "exp-p14",
  code: "P14",
  family: "CANONICAL",
  label: "P14 preset",
  description: "d",
  indexRequirements: null,
  requiredCapabilities: [],
  supported: true,
  supportStatus: "EXECUTABLE",
  reasonIfUnsupported: null,
  requiresMultiTurn: false,
  mapsToRuntimeCapabilities: {},
  allowedOutcomes: ["EXECUTED"],
  chatSelectable: true,
  labSelectable: true,
  labOnly: false,
  protocolStageIndex: 14,
};

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

describe("AssistantConfigurationLayout", () => {
  beforeEach(() => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useClassifierModelsQuery.mockReturnValue({ data: [], isError: false, isLoading: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: { capabilities: [] },
      isLoading: false,
    });
  });

  it("does not render forbidden evaluation preset latency warning copy", async () => {
    const user = userEvent.setup();
    useChatToolbarStore.setState({
      api: {
        projectId: "p1",
        conversationId: "c1",
        runtimeOverride: {},
        runtimeState: {
          conversationId: "c1",
          selectedPresetId: "exp-p14",
          effectivePresetId: "exp-p14",
          preset: {
            kind: "EXPERIMENTAL",
            code: "P14",
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
        compatibleExperimentalPresets: [
          {
            preset: experimentalP14Preset,
            compatibility: {
              selectable: true,
              disabledReasonCode: null,
              disabledReason: null,
              indexRequirements: null,
              compatibleWithActiveIndex: true,
            },
          },
        ],
        experimentalPresets: [experimentalP14Preset],
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

    renderSubject();
    await user.click(screen.getByTestId("chat-config-edit-button"));

    expect(screen.queryByTestId("chat-preset-latency-warning")).not.toBeInTheDocument();
    expect(screen.queryByText(/live demos/i)).not.toBeInTheDocument();
    expect(screen.getByTestId("chat-preset-select")).toBeInTheDocument();
    expect(screen.getByTestId("chat-llm-model-select")).toBeInTheDocument();
  });

  it("keeps model selector in a width-constrained container with long model names", async () => {
    const user = userEvent.setup();
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
        llmModelChoice: LONG_MODEL,
        setLlmModelChoice: vi.fn(),
        classifierModelChoice: "",
        setClassifierModelChoice: vi.fn(),
        selectableLlmModels: [
          {
            modelName: LONG_MODEL,
            displayName: LONG_MODEL,
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

    renderSubject();
    await user.click(screen.getByTestId("chat-config-edit-button"));

    const select = screen.getByTestId("chat-llm-model-select");
    expect(select.className).toMatch(/w-full/);
    expect(select.className).toMatch(/min-w-0/);
    expect(select).toHaveValue(LONG_MODEL);
    expect(screen.getByTestId("chat-config-summary-model").className).toMatch(/break-words/);
  });
});
