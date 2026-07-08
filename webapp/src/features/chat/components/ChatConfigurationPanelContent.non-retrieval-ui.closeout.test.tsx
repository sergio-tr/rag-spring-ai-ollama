import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { mockChatToolbarApi } from "@/test-utils/chat-toolbar-api-mock";
import { mockChatRuntimeState } from "@/test-utils/chat-runtime-state-mock";
import { compatiblePresetsQueryMock } from "@/test-utils/compatible-presets-mock";
import type { ChatToolbarApi } from "@/features/chat/store/chat-toolbar.store";

const hooksMock = vi.hoisted(() => ({
  useRuntimeConfigCapabilities: vi.fn(),
  useProjectIndexProfile: vi.fn(),
  useActiveProjectSnapshot: vi.fn(),
  useClassifierModelsQuery: vi.fn(),
  useMeEffectiveEmbeddingDefaults: vi.fn(),
  useProjectStoredRagConfigQuery: vi.fn(),
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
vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", () => ({
  useMeEffectiveEmbeddingDefaults: (...args: unknown[]) => hooksMock.useMeEffectiveEmbeddingDefaults(...args),
}));
vi.mock("@/features/settings/hooks/use-rag-config", () => ({
  useProjectStoredRagConfigQuery: (...args: unknown[]) => hooksMock.useProjectStoredRagConfigQuery(...args),
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

function baseApi(saveRuntimeOverride = vi.fn(), overrides: Partial<ChatToolbarApi> = {}) {
  return mockChatToolbarApi({
    saveRuntimeOverride,
    runtimeState: mockChatRuntimeState({
      preset: {
        kind: "MISSING",
        code: null,
        label: "Direct LLM baseline",
        chatSelectable: false,
        supported: false,
        supportStatus: null,
        reasonIfUnsupported: null,
      },
      baseEffectiveConfig: { useRetrieval: false, topK: 5, similarityThreshold: 0.7 },
      effectiveConfig: { useRetrieval: false, topK: 5, similarityThreshold: 0.7 },
      effectiveRetrievalParameters: {
        topK: 5,
        similarityThreshold: 0.7,
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
          materializationStrategy: "CHUNK_LEVEL",
          supportsMetadata: false,
          embeddingModelId: "mxbai",
          chunkMaxChars: 400,
          chunkOverlap: 40,
        },
      },
    }),
    ...overrides,
  });
}

describe("ChatConfigurationPanelContent closeout D — non-retrieval UI", () => {
  beforeEach(() => {
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({
      data: { capabilities: [] },
      isLoading: false,
      isError: false,
    });
    hooksMock.useProjectIndexProfile.mockReturnValue({
      data: { materializationStrategy: "CHUNK_LEVEL", metadataEnabled: false },
      isLoading: false,
      isError: false,
    });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null, isLoading: false, isError: false });
    hooksMock.useClassifierModelsQuery.mockReturnValue({ data: [], isLoading: false, isError: false });
    hooksMock.useMeEffectiveEmbeddingDefaults.mockReturnValue({
      data: {
        retrievalOptions: { topK: 8, similarityThreshold: 0.35 },
      },
      isLoading: false,
      isError: false,
    });
    hooksMock.useProjectStoredRagConfigQuery.mockReturnValue({ data: {}, isLoading: false, isError: false });
    useChatToolbarStore.setState({ api: null });
  });

  it("Direct LLM/Baseline hides topK/threshold controls and shows not-applicable note", () => {
    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    expect(screen.getByTestId("chat-retrieval-settings-not-applicable")).toHaveTextContent(
      /Retrieval settings do not apply/i,
    );
    expect(screen.queryByTestId("chat-retrieval-override-mode")).not.toBeInTheDocument();
    expect(screen.queryByTestId("chat-runtime-toggle-topK")).not.toBeInTheDocument();
    expect(screen.queryByTestId("chat-runtime-toggle-similarityThreshold")).not.toBeInTheDocument();
  });

  it("CHUNK retrieval preset still shows topK/threshold controls", () => {
    useChatToolbarStore.setState({
      api: baseApi(vi.fn(), {
        runtimeState: mockChatRuntimeState({
          baseEffectiveConfig: { useRetrieval: true, topK: 8, similarityThreshold: 0.7 },
          effectiveConfig: { useRetrieval: true, topK: 8, similarityThreshold: 0.7 },
        }),
      }),
    });
    renderSubject();
    fireEvent.click(screen.getByTestId("chat-config-edit-button"));
    expect(screen.queryByTestId("chat-retrieval-settings-not-applicable")).not.toBeInTheDocument();
    expect(screen.getByTestId("chat-retrieval-override-mode")).toBeInTheDocument();
    expect(screen.getByTestId("chat-runtime-toggle-topK")).toBeInTheDocument();
    expect(screen.getByTestId("chat-runtime-toggle-similarityThreshold")).toBeInTheDocument();
  });

  it("existing STRUCT project loads with legacy warning", () => {
    useChatToolbarStore.setState({
      api: baseApi(vi.fn(), {
        projectCompatiblePresets: {
          ...compatiblePresetsQueryMock.data,
          activeSnapshotCapabilities: {
            materializationStrategy: "STRUCTURED_SEARCH",
            supportsMetadata: true,
            embeddingModelId: "mxbai",
            chunkMaxChars: 400,
            chunkOverlap: 40,
          },
        },
      }),
    });
    renderSubject();
    expect(screen.getByTestId("chat-structured-search-legacy-warning")).toHaveTextContent(
      /not intended for standard RAG chat over documents/i,
    );
  });
});
