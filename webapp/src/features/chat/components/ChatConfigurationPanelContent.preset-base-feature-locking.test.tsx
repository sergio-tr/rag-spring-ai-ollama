import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { mockChatRuntimeState } from "@/test-utils/chat-runtime-state-mock";
import { mockChatToolbarApi } from "@/test-utils/chat-toolbar-api-mock";

const hooksMock = vi.hoisted(() => ({
  useRuntimeConfigCapabilities: vi.fn(),
  useProjectIndexProfile: vi.fn(),
  useActiveProjectSnapshot: vi.fn(),
  useMeEffectiveEmbeddingDefaults: vi.fn(),
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
vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", () => ({
  useMeEffectiveEmbeddingDefaults: (...args: unknown[]) => hooksMock.useMeEffectiveEmbeddingDefaults(...args),
}));

const stubCap = (key: string) => ({
  key,
  label: key,
  description: "",
  category: "RUNTIME_HOT_SWAPPABLE" as const,
  visibleInChat: true,
  configurableInChat: true,
  implemented: true,
  engineWired: true,
  supportMode: null,
  displayOrder: 10,
  requires: [],
  excludes: [],
  requiresIndexSnapshot: false,
  requiresReindexWhenChanged: false,
  reasonIfDisabled: null as string | null,
  reasonIfNotImplemented: null as string | null,
});

function chunkRagRuntimeState() {
  return mockChatRuntimeState({
    selectedPresetId: "cafe0001-0001-4001-8001-000000000013",
    effectivePresetId: "cafe0001-0001-4001-8001-000000000013",
    preset: {
      kind: "EXPERIMENTAL",
      code: "P3",
      label: "Chunk RAG",
      chatSelectable: true,
      supported: true,
      supportStatus: null,
      reasonIfUnsupported: null,
    },
    baseEffectiveConfig: {
      useRetrieval: true,
      expansionEnabled: false,
      toolsEnabled: false,
    },
    effectiveConfig: {
      useRetrieval: true,
      expansionEnabled: false,
      toolsEnabled: false,
    },
    effectiveRetrievalParameters: {
      topK: 10,
      similarityThreshold: 0.7,
      topKSource: "PRESET_LOCKED",
      similarityThresholdSource: "PRESET_LOCKED",
    },
    selectedWorkflow: "dense",
    indexCompatibility: {
      activeProjectSnapshotId: "snap-1",
      activeConversationSnapshotId: null,
      activeIndexProfileHash: "hash-1",
      activeIndexProfile: {},
      hasActiveIndex: true,
      activeSnapshotCapabilities: {
        materializationStrategy: "CHUNK_LEVEL",
        supportsMetadata: false,
        embeddingModelId: "mxbai",
        chunkMaxChars: 400,
        chunkOverlap: 40,
      },
    },
    disabledRuntimeFeatures: [
      {
        key: "useRetrieval",
        reasonCode: "PRESET_BASE_FEATURE_LOCKED",
        reason: "Enabled by preset",
      },
      {
        key: "expansionEnabled",
        reasonCode: "PRESET_FEATURE_TOGGLE_DEFERRED",
        reason: "Controlled by preset",
      },
    ],
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

describe("ChatConfigurationPanelContent preset base feature locking", () => {
  beforeEach(() => {
    hooksMock.useMeEffectiveEmbeddingDefaults.mockReturnValue({ data: null });
    hooksMock.useProjectIndexProfile.mockReturnValue({
      data: { materializationStrategy: "CHUNK_LEVEL", metadataEnabled: false },
      isLoading: false,
      isError: false,
    });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [stubCap("useRetrieval"), stubCap("expansionEnabled"), stubCap("toolsEnabled")],
      },
      isLoading: false,
    });

    useChatToolbarStore.setState({
      api: mockChatToolbarApi({
        runtimeState: chunkRagRuntimeState(),
      }),
    });
  });

  it("locks useRetrieval with Enabled by preset badge", async () => {
    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));

    const retrievalToggle = screen.getByTestId("chat-runtime-toggle-useRetrieval") as HTMLInputElement;
    expect(retrievalToggle.checked).toBe(true);
    expect(retrievalToggle.disabled).toBe(true);
    expect(screen.getByTestId("chat-runtime-preset-badge-useRetrieval")).toHaveTextContent("Enabled by preset");
  });

  it("defers optional add-on toggles", async () => {
    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));

    const expansionToggle = screen.getByTestId("chat-runtime-toggle-expansionEnabled") as HTMLInputElement;
    expect(expansionToggle.checked).toBe(false);
    expect(expansionToggle.disabled).toBe(true);
    expect(screen.getByTestId("chat-runtime-disable-tip-expansionEnabled")).toBeInTheDocument();
  });
});

function demoBestRuntimeState() {
  return mockChatRuntimeState({
    selectedPresetId: "cafe0001-0001-4001-8001-000000000003",
    effectivePresetId: "cafe0001-0001-4001-8001-000000000003",
    preset: {
      kind: "PRODUCT",
      code: "Demo_Best",
      label: "Demo_Best",
      chatSelectable: true,
      supported: true,
      supportStatus: null,
      reasonIfUnsupported: null,
    },
    baseEffectiveConfig: {
      useRetrieval: true,
      toolsEnabled: true,
      functionCallingEnabled: true,
      useAdvisor: true,
      expansionEnabled: true,
      nerEnabled: true,
      postRetrievalEnabled: true,
      clarificationEnabled: true,
      rankerEnabled: false,
      judgeEnabled: false,
      memoryEnabled: false,
      reasoningEnabled: false,
      adaptiveRoutingEnabled: false,
    },
    effectiveConfig: {
      useRetrieval: true,
      toolsEnabled: true,
      functionCallingEnabled: true,
      useAdvisor: true,
      expansionEnabled: true,
      nerEnabled: true,
      postRetrievalEnabled: true,
      clarificationEnabled: true,
      rankerEnabled: false,
      judgeEnabled: false,
      memoryEnabled: false,
      reasoningEnabled: false,
      adaptiveRoutingEnabled: false,
    },
    effectiveRetrievalParameters: {
      topK: 12,
      similarityThreshold: 0.1,
      topKSource: "PRESET_LOCKED",
      similarityThresholdSource: "PRESET_LOCKED",
    },
    indexCompatibility: {
      activeProjectSnapshotId: "snap-1",
      activeConversationSnapshotId: null,
      activeIndexProfileHash: "hash-1",
      activeIndexProfile: {},
      hasActiveIndex: true,
      activeSnapshotCapabilities: {
        materializationStrategy: "HYBRID",
        supportsMetadata: true,
        embeddingModelId: "mxbai",
        chunkMaxChars: 400,
        chunkOverlap: 40,
      },
    },
  });
}

describe("ChatConfigurationPanelContent Demo_Best preset locking", () => {
  beforeEach(() => {
    hooksMock.useMeEffectiveEmbeddingDefaults.mockReturnValue({ data: null });
    hooksMock.useProjectIndexProfile.mockReturnValue({
      data: { materializationStrategy: "HYBRID", metadataEnabled: true },
      isLoading: false,
      isError: false,
    });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: {
        capabilities: [
          stubCap("useRetrieval"),
          stubCap("toolsEnabled"),
          stubCap("functionCallingEnabled"),
          stubCap("useAdvisor"),
          stubCap("rankerEnabled"),
          stubCap("judgeEnabled"),
          stubCap("memoryEnabled"),
        ],
      },
      isLoading: false,
    });
    useChatToolbarStore.setState({
      api: mockChatToolbarApi({ runtimeState: demoBestRuntimeState() }),
    });
  });

  it("locks Demo_Best enabled base features", async () => {
    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));

    expect((screen.getByTestId("chat-runtime-toggle-useRetrieval") as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByTestId("chat-runtime-toggle-toolsEnabled") as HTMLInputElement).disabled).toBe(true);
    expect(screen.getByTestId("chat-runtime-preset-badge-useRetrieval")).toBeInTheDocument();
  });

  it("defers Demo_Best latency-off features", async () => {
    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));

    expect((screen.getByTestId("chat-runtime-toggle-rankerEnabled") as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByTestId("chat-runtime-toggle-judgeEnabled") as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByTestId("chat-runtime-toggle-memoryEnabled") as HTMLInputElement).disabled).toBe(true);
  });
});
