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

describe("chat config layout", () => {
  beforeEach(() => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: { capabilities: [] },
      isLoading: false,
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
    render(
      <IntlTestProvider locale="en">
        <ChatConfigurationSidePanel open onClose={() => undefined} />
      </IntlTestProvider>,
    );

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

    const select = screen.getByTestId("chat-llm-model-select");
    expect(select).toHaveAttribute("data-effective-provider", "OPENAI_COMPATIBLE");
    expect(screen.getByTestId("chat-llm-model-provider")).toHaveTextContent(/Configured model provider/i);
    expect(screen.getByRole("option", { name: /gpt-oss:20b/i })).toBeInTheDocument();
  });
});
