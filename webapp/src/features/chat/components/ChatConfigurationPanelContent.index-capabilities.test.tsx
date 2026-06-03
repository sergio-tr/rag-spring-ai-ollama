import { describe, it, expect, beforeEach, vi } from "vitest";
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

async function openTechnicalDetails() {
  const user = userEvent.setup();
  const details = screen.getByTestId("chat-config-technical-details");
  if (!details.hasAttribute("open")) {
    await user.click(within(details).getByText(/Technical details/i));
  }
  return { user, details };
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
    reasonIfDisabled: "Index snapshot compatibility; changing requires reindex.",
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

  it("shows compact summary without exposing profile hash by default", () => {
    hooksMock.useActiveProjectSnapshot.mockReturnValue({
      data: { id: "snap-1", status: "ACTIVE", indexProfileHash: "h1" },
      isLoading: false,
      isError: false,
    });
    renderSubject();
    expect(screen.getByTestId("chat-config-compact-summary")).toBeInTheDocument();
    expect(screen.getByTestId("chat-config-summary-index")).toBeInTheDocument();
    const technical = screen.getByTestId("chat-config-technical-details");
    expect(technical).not.toHaveAttribute("open");
    expect(within(technical).getByText("h1")).not.toBeVisible();
    const effectiveKeys = screen.queryByTestId("chat-config-effective-keys");
    if (effectiveKeys) {
      expect(effectiveKeys).not.toBeVisible();
    }
  });

  it("shows a hint when there is no active snapshot yet inside technical details", async () => {
    renderSubject();
    const { details } = await openTechnicalDetails();
    expect(within(details).getByText(/No active index snapshot yet/i)).toBeInTheDocument();
  });

  it("renders active snapshot details including profile hash when technical details are open", async () => {
    hooksMock.useActiveProjectSnapshot.mockReturnValue({
      data: { id: "snap-1", status: "ACTIVE", indexProfileHash: "h1" },
      isLoading: false,
      isError: false,
    });
    renderSubject();
    const { details } = await openTechnicalDetails();
    expect(within(details).getByText(/Active snapshot/i)).toBeInTheDocument();
    expect(within(details).getByText("snap-1")).toBeInTheDocument();
    expect(within(details).getByText("ACTIVE")).toBeInTheDocument();
    expect(within(details).getByText("h1")).toBeInTheDocument();
  });

  it("renders preset requirements + compatibility and shows reindex-required callout", async () => {
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
    const { details } = await openTechnicalDetails();
    expect(within(details).getByText(/Preset index requirements/i)).toBeInTheDocument();
    expect(within(details).getByText("HYBRID")).toBeInTheDocument();
    expect(within(details).getByText("INCOMPATIBLE")).toBeInTheDocument();
    expect(within(details).getByText(/Reindex required for this preset/i)).toBeInTheDocument();
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
    const { details } = await openTechnicalDetails();
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
    expect(screen.getByTestId("chat-preset-support-badge")).toHaveTextContent("NOT_SUPPORTED");
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
    expect(screen.getByText("Custom")).toBeInTheDocument();
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
    const sel = screen.getByRole("combobox", { name: /preset/i }) as HTMLSelectElement;
    expect(sel.value).toBe("preset-missing-id");
    // The synthetic option should be present so UI doesn't appear blank.
    expect(screen.getByRole("option", { name: "Prior Preset Label" })).toBeInTheDocument();
  });

  it("renders experimental preset option labels for multi-turn and not selectable presets", async () => {
    useChatToolbarStore.setState((s) => ({
      api: {
        ...s.api!,
        experimentalPresets: [
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
        ],
      },
    }));
    renderSubject();
    await openEditPanel();
    expect(screen.getByText(/P13 — Multi turn \[REQUIRES_MULTI_TURN\]/)).toBeInTheDocument();
    expect(screen.getByText(/P0 — Hidden \[NOT_SUPPORTED: not allowed\]/)).toBeInTheDocument();
    // Supported selectable presets should render the base label without brackets.
    expect(screen.getByText(/P2 — Supported selectable$/)).toBeInTheDocument();
    // Not-supported but chat-selectable presets should still include status brackets.
    expect(screen.getByText(/P3 — Not supported but selectable \[NOT_SUPPORTED: incompatible index\]/)).toBeInTheDocument();
  });

  it("disables experimental preset when active index profile is incompatible", async () => {
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
        experimentalPresets: [
          {
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
          },
        ],
      },
    }));

    renderSubject();
    await openEditPanel();
    const option = screen.getByRole("option", { name: /P7 — Hybrid preset/i }) as HTMLOptionElement;
    expect(option.disabled).toBe(true);
    expect(option.textContent).toMatch(/Create or reindex the project with a compatible index profile/i);
  });

  it("shows blocking issue banner and selected preset reindex CTA from runtime-state", async () => {
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
              message: "This preset requires a HYBRID index.",
              severity: "ERROR",
            },
          ],
          presetCompatibility: {
            selectable: false,
            disabledReasonCode: "MATERIALIZATION_NOT_SUPPORTED",
            disabledReason: "This preset requires a HYBRID index.",
            indexRequirements: {
              requiredMaterializationStrategy: "HYBRID",
              requiresMetadataSupport: false,
            },
            compatibleWithActiveIndex: false,
          },
          disabledPresetReason: "This preset requires a HYBRID index.",
        },
      },
    }));

    renderSubject();
    expect(screen.getByTestId("chat-runtime-blocking-banner")).toHaveTextContent("This preset requires a HYBRID index.");
    await openEditPanel();
    expect(screen.getByTestId("chat-preset-select")).toBeInTheDocument();
    expect(screen.getByText(/Create or reindex project with compatible profile/i)).toBeInTheDocument();
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

