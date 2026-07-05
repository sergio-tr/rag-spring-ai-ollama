import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { IntlTestProvider } from "@/test-utils/intl";
import type {
  CompatibleExperimentalPresetDto,
  ExperimentalPresetCatalogItemDto,
  ProjectCompatiblePresetsDto,
} from "@/types/api";

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

async function openAdvancedTechnical() {
  const user = userEvent.setup();
  const advanced = screen.getByTestId("chat-config-advanced-technical");
  if (!advanced.hasAttribute("open")) {
    await user.click(within(advanced).getByText(/Advanced technical details/i));
  }
  return { user, advanced };
}

async function openEditPanel() {
  const user = userEvent.setup();
  if (!screen.queryByTestId("chat-preset-select")) {
    await user.click(screen.getByTestId("chat-config-edit-button"));
  }
  return user;
}

function indexBoundCap(key: string, label: string, displayOrder: number) {
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
    displayOrder,
    requires: [] as string[],
    excludes: [] as string[],
    requiresIndexSnapshot: true,
    requiresReindexWhenChanged: true,
    reasonIfDisabled: "Index settings are fixed at project creation.",
    reasonIfNotImplemented: null,
  };
}

describe("ChatConfigurationPanelContent index capabilities", () => {
  beforeEach(() => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          indexBoundCap("materializationStrategy", "Materialization strategy", 110),
          indexBoundCap("metadataEnabled", "Metadata index", 120),
          indexBoundCap("embeddingModel", "Embedding model", 125),
          indexBoundCap("chunkMaxChars", "Chunk size (max chars)", 126),
          indexBoundCap("chunkOverlap", "Chunk overlap (chars)", 127),
        ],
      },
      isLoading: false,
    });
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
          baseEffectiveConfig: { useRetrieval: false },
          effectiveConfig: { useRetrieval: false, materializationStrategy: "CHUNK_LEVEL", metadataEnabled: false },
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
  });

  it("shows compact summary without exposing configuration identifiers by default", () => {
    hooksMock.useActiveProjectSnapshot.mockReturnValue({
      data: { id: "snap-1", status: "ACTIVE", indexProfileHash: "h1" },
      isLoading: false,
      isError: false,
    });
    renderSubject();
    expect(screen.getByTestId("chat-config-compact-summary")).toBeInTheDocument();
    expect(screen.getByTestId("chat-config-summary-index")).toHaveTextContent(/Ready/i);
    const currentSettings = screen.getByTestId("chat-config-current-settings");
    expect(currentSettings).not.toHaveAttribute("open");
    const hashNode = within(currentSettings).queryByText("h1");
    if (hashNode) {
      expect(hashNode).not.toBeVisible();
    }
    const jsonNode = screen.queryByTestId("chat-config-effective-json");
    if (jsonNode) {
      expect(jsonNode).not.toBeVisible();
    }
  });

  it("shows a hint when there is no active snapshot yet inside advanced technical details", async () => {
    renderSubject();
    const { advanced } = await openAdvancedTechnical();
    expect(within(advanced).getByText(/No active search index yet/i)).toBeInTheDocument();
  });

  it("renders saved configuration state and configuration identifier when advanced technical is open", async () => {
    hooksMock.useActiveProjectSnapshot.mockReturnValue({
      data: { id: "snap-1", status: "ACTIVE", indexProfileHash: "h1" },
      isLoading: false,
      isError: false,
    });
    renderSubject();
    const { advanced } = await openAdvancedTechnical();
    expect(within(advanced).getByText(/Saved configuration state/i)).toBeInTheDocument();
    expect(within(advanced).getByText("snap-1")).toBeInTheDocument();
    expect(within(advanced).getByText("ACTIVE")).toBeInTheDocument();
    expect(within(advanced).getByText("h1")).toBeInTheDocument();
  });

  it("renders preset requirements + compatibility and shows fixed-index callout", async () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...s.api!.runtimeState!,
          requiresReindex: true,
          indexCompatibility: {
            activeProjectSnapshotId: null,
            activeConversationSnapshotId: null,
            activeIndexProfileHash: null,
            activeIndexProfile: {},
            hasActiveIndex: false,
            compatibilityStatus: "INCOMPATIBLE",
            presetIndexRequirements: {
              requiredMaterializationStrategy: "HYBRID",
              requiresMetadataSupport: true,
            },
          },
        },
      },
    }));

    renderSubject();
    const { advanced: details } = await openAdvancedTechnical();
    expect(within(details).getByText(/Index requirements for selected profile/i)).toBeInTheDocument();
    expect(within(details).getByText("HYBRID")).toBeInTheDocument();
    expect(within(details).getByText("INCOMPATIBLE")).toBeInTheDocument();
    expect(within(details).getByText(/Index profile incompatible with selected configuration/i)).toBeInTheDocument();
  });

  it("falls back to effectiveConfig values when project index profile is not loaded", async () => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null, isLoading: false, isError: false });
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...s.api!.runtimeState!,
          effectiveConfig: { ...s.api!.runtimeState!.effectiveConfig, materializationStrategy: "FULL_TEXT", metadataEnabled: true },
        },
      },
    }));

    renderSubject();
    const { advanced: details } = await openAdvancedTechnical();
    expect(within(details).getByText(/Materialization strategy/i)).toBeInTheDocument();
    expect(within(details).getAllByText("FULL_TEXT").length).toBeGreaterThan(0);
    expect(within(details).getByText(/Metadata index/i)).toBeInTheDocument();
  });

  it("renders preset kind badges for PRODUCT / EXPERIMENTAL / MISSING", async () => {
    renderSubject();
    await openEditPanel();
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...s.api!.runtimeState!,
          preset: { ...s.api!.runtimeState!.preset!, kind: "PRODUCT" },
        },
      },
    }));
    expect(await screen.findByText("Product")).toBeInTheDocument();

    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...s.api!.runtimeState!,
          preset: { ...s.api!.runtimeState!.preset!, kind: "EXPERIMENTAL" },
        },
      },
    }));
    expect(await screen.findByText("Experimental")).toBeInTheDocument();

    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...s.api!.runtimeState!,
          preset: { ...s.api!.runtimeState!.preset!, kind: "MISSING" },
        },
      },
    }));
    expect(await screen.findByText("Missing")).toBeInTheDocument();
  });

  it("renders support badge when runtime preset is not chat-selectable", async () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...s.api!.runtimeState!,
          preset: {
            ...s.api!.runtimeState!.preset!,
            chatSelectable: false,
            supported: false,
            supportStatus: "NOT_SUPPORTED",
            reasonIfUnsupported: "multiturn",
          },
        },
      },
    }));
    renderSubject();
    await openEditPanel();
    expect(screen.getByTestId("chat-preset-support-badge")).toHaveTextContent(/multiturn/i);
    expect(screen.queryByText(/NOT_SUPPORTED/i)).not.toBeInTheDocument();
  });

  it("shows custom badge when runtime state is marked custom", async () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: { ...s.api!.runtimeState!, isCustom: true },
      },
    }));
    renderSubject();
    await openEditPanel();
    expect(screen.getByText("Custom configuration for this conversation")).toBeInTheDocument();
  });

  it("shows synthetic selected preset option when selectedPresetId is not in catalogs", async () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...s.api!.runtimeState!,
          selectedPresetId: "preset-missing-id",
          preset: { ...s.api!.runtimeState!.preset!, label: "Prior Preset Label" },
        },
      },
    }));
    renderSubject();
    await openEditPanel();
    const sel = screen.getByRole("combobox", { name: /configuration profile/i }) as HTMLSelectElement;
    expect(sel.value).toBe("preset-missing-id");
    // The synthetic option should be present so UI doesn't appear blank.
    expect(screen.getByRole("option", { name: "Prior Preset Label" })).toBeInTheDocument();
  });

  it("renders experimental preset option labels for multi-turn and not selectable presets", async () => {
    const experimentalPresets: ExperimentalPresetCatalogItemDto[] = [
      {
        productPresetId: "exp-1",
        code: "P13",
        family: "CANONICAL",
        label: "Multi turn",
        description: "d",
        indexRequirements: null,
        requiredCapabilities: [],
        supported: false,
        supportStatus: "NOT_SUPPORTED",
        reasonIfUnsupported: "requires multi turn",
        requiresMultiTurn: true,
        mapsToRuntimeCapabilities: {},
        allowedOutcomes: ["NOT_SUPPORTED"],
        chatSelectable: false,
        labSelectable: true,
        labOnly: true,
      },
      {
        productPresetId: "exp-2",
        code: "P0",
        family: "CANONICAL",
        label: "Hidden",
        description: "d",
        indexRequirements: null,
        requiredCapabilities: [],
        supported: false,
        supportStatus: "NOT_SUPPORTED",
        reasonIfUnsupported: "not allowed",
        requiresMultiTurn: false,
        mapsToRuntimeCapabilities: {},
        allowedOutcomes: ["NOT_SUPPORTED"],
        chatSelectable: false,
        labSelectable: true,
        labOnly: true,
      },
      {
        productPresetId: "exp-3",
        code: "P2",
        family: "CANONICAL",
        label: "Supported selectable",
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
        labOnly: true,
      },
      {
        productPresetId: "exp-4",
        code: "P3",
        family: "CANONICAL",
        label: "Not supported but selectable",
        description: "d",
        indexRequirements: null,
        requiredCapabilities: [],
        supported: false,
        supportStatus: "NOT_SUPPORTED",
        reasonIfUnsupported: "incompatible index",
        requiresMultiTurn: false,
        mapsToRuntimeCapabilities: {},
        allowedOutcomes: ["NOT_SUPPORTED"],
        chatSelectable: true,
        labSelectable: true,
        labOnly: true,
      },
    ];
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        experimentalPresets,
        compatibleExperimentalPresets: experimentalPresets.map(
          (preset): CompatibleExperimentalPresetDto => ({
            preset,
            compatibility: {
              selectable: preset.chatSelectable && preset.supported,
              disabledReasonCode:
                preset.chatSelectable && preset.supported ? null : "PRESET_NOT_SELECTABLE",
              disabledReason: preset.reasonIfUnsupported,
              indexRequirements: preset.indexRequirements,
              compatibleWithActiveIndex: true,
            },
          }),
        ),
      },
    }));
    renderSubject();
    const user = await openEditPanel();
    await user.click(screen.getByTestId("chat-preset-show-incompatible"));
    expect(screen.getByText(/Multi turn \(Advanced\) \(requires multi turn\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Hidden \(Fast\) \(not allowed\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Supported selectable \(Fast\)/)).toBeInTheDocument();
    expect(screen.getByText(/Not supported but selectable \(Standard\) \(incompatible index\)/i)).toBeInTheDocument();
    expect(screen.queryByText(/REQUIRES_MULTI_TURN/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/\[NOT_SUPPORTED/i)).not.toBeInTheDocument();
  });

  it("disables experimental preset when active index profile is incompatible", async () => {
    const hybridPreset: ExperimentalPresetCatalogItemDto = {
      productPresetId: "exp-hybrid",
      code: "P7",
      family: "CANONICAL",
      label: "Hybrid preset",
      description: "d",
      indexRequirements: {
        requiredMaterializationStrategy: "HYBRID",
        requiresMetadataSupport: true,
      },
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
    };
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...s.api!.runtimeState!,
          indexCompatibility: {
            activeProjectSnapshotId: "snap-1",
            activeConversationSnapshotId: null,
            activeIndexProfileHash: "h1",
            activeIndexProfile: {},
            hasActiveIndex: true,
            activeSnapshotCapabilities: {
              materializationStrategy: "CHUNK_LEVEL",
              supportsMetadata: false,
              embeddingModelId: "mxbai",
              chunkMaxChars: 400,
              chunkOverlap: null,
            },
            presetIndexRequirements: null,
            compatibleWithPreset: true,
            compatibilityStatus: "OK",
          },
        },
        experimentalPresets: [hybridPreset],
        compatibleExperimentalPresets: [
          {
            preset: hybridPreset,
            compatibility: {
              selectable: false,
              disabledReasonCode: "MATERIALIZATION_NOT_SUPPORTED",
              disabledReason: "Requires HYBRID index.",
              indexRequirements: {
                requiredMaterializationStrategy: "HYBRID",
                requiresMetadataSupport: true,
              },
              compatibleWithActiveIndex: false,
            },
          },
        ],
        projectCompatiblePresets: {
          projectId: "p1",
          effectiveEmbeddingModelId: "mxbai",
          hasActiveIndex: true,
          readyDocumentCount: 1,
          activeSnapshotCapabilities: {
            materializationStrategy: "CHUNK_LEVEL",
            supportsMetadata: false,
            embeddingModelId: "mxbai",
            chunkMaxChars: 400,
            chunkOverlap: null,
          },
          productPresets: [],
          experimentalPresets: [
            {
              preset: hybridPreset,
              compatibility: {
                selectable: false,
                disabledReasonCode: "MATERIALIZATION_NOT_SUPPORTED",
                disabledReason: "Requires HYBRID index.",
                indexRequirements: {
                  requiredMaterializationStrategy: "HYBRID",
                  requiresMetadataSupport: true,
                },
                compatibleWithActiveIndex: false,
              },
            },
          ],
        } satisfies ProjectCompatiblePresetsDto,
      },
    }));

    renderSubject();
    const user = await openEditPanel();
    await user.click(screen.getByTestId("chat-preset-show-incompatible"));
    const option = screen.getByRole("option", { name: /Hybrid preset/i }) as HTMLOptionElement;
    expect(option.disabled).toBe(true);
    expect(option.textContent).toMatch(/Requires HYBRID index/i);
  });

  it("shows blocking issue banner and fixed-index hint when preset incompatible", async () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        runtimeState: {
          ...s.api!.runtimeState!,
          isValid: false,
          requiresReindex: true,
          blockingIssues: [
            {
              code: "MATERIALIZATION_NOT_SUPPORTED",
              field: "presetId",
              message: "Requires HYBRID index.",
              severity: "ERROR",
            },
          ],
          presetCompatibility: {
            selectable: false,
            disabledReasonCode: "MATERIALIZATION_NOT_SUPPORTED",
            disabledReason: "Requires HYBRID index.",
            indexRequirements: {
              requiredMaterializationStrategy: "HYBRID",
              requiresMetadataSupport: false,
            },
            compatibleWithActiveIndex: false,
          },
          disabledPresetReason: "Requires HYBRID index.",
        },
      },
    }));

    renderSubject();
    expect(screen.getByTestId("chat-runtime-blocking-banner")).toHaveTextContent(
      /compatible index profile for the selected preset/i,
    );
    await openEditPanel();
    expect(screen.getByTestId("chat-preset-select")).toBeInTheDocument();
    expect(screen.getByTestId("chat-preset-incompatible-fixed-index-hint")).toHaveTextContent(
      /Index settings are fixed after project creation/i,
    );
    expect(screen.queryByTestId("chat-preset-incompatible-fixed-index-hint")?.textContent ?? "").not.toMatch(/reindex/i);
  });

  it("shows preset catalog empty message when presets are loaded and empty", async () => {
    useChatToolbarStore.setState((s) => ({
      api: { ...s.api!, presetsLoading: false, presetsError: false, presets: [] },
    }));
    renderSubject();
    await openEditPanel();
    expect(screen.getByTestId("chat-preset-select")).toBeInTheDocument();
    expect(screen.getByText(/No presets are available/i)).toBeInTheDocument();
  });
});

