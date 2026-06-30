import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { ChatAssistantMessageExtras } from "./ChatAssistantMessageExtras";
import { useChatToolbarStore, type ChatToolbarApi } from "@/features/chat/store/chat-toolbar.store";
import { IntlTestProvider } from "@/test-utils/intl";
import { ADVANCED_TECHNICAL_DETAILS_TITLE } from "@/lib/product-provider-labels";
import { FORBIDDEN_NORMAL_UI_STRING_PATTERNS } from "@/lib/forbidden-primary-ui-strings";
import type { ChatRuntimeStateDto, MessageDto } from "@/types/api";

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

function renderChatConfig() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider>
        <ChatConfigurationPanelContent />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

function baseChatToolbarApi(overrides: Partial<ChatToolbarApi> = {}): ChatToolbarApi {
  return {
    projectId: "p1",
    conversationId: "c1",
    runtimeOverride: {},
    runtimeState: {
      conversationId: "c1",
      selectedPresetId: "preset-1",
      effectivePresetId: "preset-1",
      effectiveConfig: { topK: 5, chatModel: "gpt-test" },
      manualOverrideKeys: ["topK"],
      baseEffectiveConfig: { topK: 3 },
      conversationLlmModel: null,
      conversationClassifierModelId: null,
      conversationModelsPinned: false,
      runtimeOverride: {},
      isCustom: false,
      validation: { valid: true, supported: true, errors: [], warnings: [] },
      selectedWorkflow: null,
      blockingIssues: [],
      warnings: [],
      preset: {
        kind: "PRODUCT",
        code: null,
        label: "Production assistant configuration",
        chatSelectable: true,
        supported: true,
        supportStatus: "EXECUTABLE",
        reasonIfUnsupported: null,
      },
      indexCompatibility: {
        activeProjectSnapshotId: "snap-hidden-1",
        activeConversationSnapshotId: null,
        activeIndexProfileHash: "hash-hidden-1",
        activeIndexProfile: {},
        hasActiveIndex: true,
        compatibilityStatus: "COMPATIBLE",
        activeSnapshotCapabilities: {
          materializationStrategy: "HYBRID",
          supportsMetadata: true,
          embeddingModelId: "emb-1",
          chunkMaxChars: 1000,
          chunkOverlap: null,
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
    documents: [
      {
        id: "d1",
        status: "READY",
        fileName: "a.pdf",
        chunkCount: null,
        errorMessage: null,
        uploadedAt: "2025-01-01T00:00:00Z",
        reindexedAt: null,
        corpusScope: "PROJECT_SHARED",
        conversationId: null,
        currentIndexSnapshotId: null,
        indexSignatureHash: null,
        storagePresent: true,
      },
    ],
    ...overrides,
  };
}

function seedChatStore(
  overrides: Omit<Partial<ChatToolbarApi>, "runtimeState"> & { runtimeState?: Partial<ChatRuntimeStateDto> } = {},
) {
  const { runtimeState: runtimeStateOverride, ...rest } = overrides;
  const base = baseChatToolbarApi();
  useChatToolbarStore.setState({
    api: baseChatToolbarApi({
      ...rest,
      runtimeState:
        runtimeStateOverride && base.runtimeState
          ? { ...base.runtimeState, ...runtimeStateOverride }
          : base.runtimeState,
    }),
  });
}

async function openChatAdvancedTechnical(user: ReturnType<typeof userEvent.setup>) {
  const advanced = screen.getByTestId("chat-config-advanced-technical");
  expect(within(advanced).getByText(ADVANCED_TECHNICAL_DETAILS_TITLE)).toBeInTheDocument();
  if (!advanced.hasAttribute("open")) {
    await user.click(within(advanced).getByText(ADVANCED_TECHNICAL_DETAILS_TITLE));
  }
  return advanced;
}

describe("Advanced technical details visibility — chat configuration", () => {
  beforeEach(() => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({
      data: { id: "snap-hidden-1", status: "ACTIVE", indexProfileHash: "hash-hidden-1" },
      isLoading: false,
      isError: false,
    });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: { capabilities: [], disabledRuntimeFeatures: [] },
      isLoading: false,
      isError: false,
    });
    seedChatStore();
  });

  it("uses the exact collapsed section label Advanced technical details", () => {
    renderChatConfig();
    const advanced = screen.getByTestId("chat-config-advanced-technical");
    expect(within(advanced).getByText(ADVANCED_TECHNICAL_DETAILS_TITLE).textContent).toBe(
      "Advanced technical details",
    );
  });

  it("does not show forbidden internal strings in the normal compact summary", () => {
    renderChatConfig();
    const summary = screen.getByTestId("chat-config-compact-summary");
    for (const pattern of FORBIDDEN_NORMAL_UI_STRING_PATTERNS) {
      expect(summary.textContent ?? "").not.toMatch(pattern);
    }
    expect(summary.textContent ?? "").not.toMatch(/\bsnapshot\b/i);
    expect(screen.queryByText("hash-hidden-1")).not.toBeVisible();
    expect(screen.queryByText("snap-hidden-1")).not.toBeVisible();
    const json = screen.queryByTestId("chat-config-effective-json");
    if (json) {
      expect(json).not.toBeVisible();
    }
  });

  it("maps function-calling precedence warnings to advanced technical details only", async () => {
    const user = userEvent.setup();
    seedChatStore({
      runtimeState: {
        warnings: [
          {
            code: "TOOLS_FUNCTION_CALLING_PRECEDENCE",
            field: null,
            message:
              "Tools and function calling are both enabled. Function calling takes precedence over deterministic tools.",
            severity: "WARNING",
          },
        ],
      },
    });
    renderChatConfig();
    const precedenceCopy = /Function calling is used when both tools and function calling are enabled/i;
    expect(screen.queryByTestId("chat-config-validation-warning")).not.toBeInTheDocument();
    expect(screen.queryByText(precedenceCopy)).not.toBeVisible();

    const advanced = await openChatAdvancedTechnical(user);
    const warning = within(advanced).getByTestId("chat-config-advanced-validation-warning");
    expect(within(warning).getByText(precedenceCopy)).toBeVisible();
    expect(warning.textContent ?? "").not.toMatch(/deterministic tool/i);
    expect(warning.textContent ?? "").not.toMatch(/takes precedence/i);
  });

  it("reveals diagnostic identifiers and JSON after expanding Advanced technical details", async () => {
    const user = userEvent.setup();
    renderChatConfig();
    const advanced = await openChatAdvancedTechnical(user);
    expect(within(advanced).getByText("snap-hidden-1")).toBeVisible();
    expect(within(advanced).getByText("hash-hidden-1")).toBeVisible();
    expect(within(advanced).getByText(/Configuration identifier/i)).toBeVisible();
    expect(screen.getByTestId("chat-config-effective-json")).toBeVisible();
  });
});

describe("Advanced technical details visibility — assistant message trace", () => {
  const message: MessageDto = {
    id: "m1",
    role: "ASSISTANT",
    content: "Answer",
    createdAt: "",
    sources: [],
    queryType: null,
    pipelineSteps: null,
    status: "DONE",
    executionMetadata: {
      traceId: "trace-diag-1",
      selectedSnapshotIds: ["snap-trace-1"],
      workflowName: "rag-workflow",
    },
  };

  it("hides trace metadata until Advanced technical details is expanded inside the message panel", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <ChatAssistantMessageExtras message={message} />
      </IntlTestProvider>,
    );
    expect(screen.queryByText("trace-diag-1")).not.toBeVisible();
    expect(screen.queryByText("snap-trace-1")).not.toBeVisible();
    await user.click(screen.getByTestId("chat-message-metadata-toggle"));
    const traceDisclosure = screen.getByTestId("chat-trace-disclosure");
    expect(within(traceDisclosure).getByText(ADVANCED_TECHNICAL_DETAILS_TITLE)).toBeInTheDocument();
    expect(screen.queryByText("trace-diag-1")).not.toBeVisible();
    await user.click(within(traceDisclosure).getByText(ADVANCED_TECHNICAL_DETAILS_TITLE));
    expect(screen.getByTestId("chat-trace")).toHaveTextContent("trace-diag-1");
    expect(screen.getByTestId("chat-trace")).toHaveTextContent("snap-trace-1");
  });
});
