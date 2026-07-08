import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { CreateConversationPresetSelector } from "./CreateConversationPresetSelector";
import { P3_PRESET_ID } from "@/features/chat/lib/preset-product-selection";

const hooksMock = vi.hoisted(() => ({
  useProjectCompatiblePresets: vi.fn(),
}));

vi.mock("@/features/chat/hooks/use-project-compatible-presets", () => ({
  useProjectCompatiblePresets: (...args: unknown[]) => hooksMock.useProjectCompatiblePresets(...args),
  buildProjectCompatiblePresetsPath: (projectId: string, embeddingModelId: string | null) =>
    `/projects/${projectId}/compatible-presets${embeddingModelId ? `?embeddingModelId=${embeddingModelId}` : ""}`,
  projectCompatiblePresetsQueryKey: (projectId: string, embeddingModelId: string | null | undefined) =>
    ["projects", projectId, "compatible-presets", embeddingModelId ?? "default"],
}));

function renderSelector(projectId = "p1") {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const onChange = vi.fn();
  render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider>
        <CreateConversationPresetSelector
          projectId={projectId}
          value=""
          onChange={onChange}
          showIncompatiblePresets={false}
          onShowIncompatiblePresetsChange={vi.fn()}
        />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
  return { onChange };
}

const compatibleCatalog = {
  data: {
    projectId: "p1",
    effectiveEmbeddingModelId: "mxbai",
    hasActiveIndex: true,
    readyDocumentCount: 1,
    activeSnapshotCapabilities: {
      materializationStrategy: "CHUNK_LEVEL",
      supportsMetadata: false,
      embeddingModelId: "mxbai",
      chunkMaxChars: 400,
      chunkOverlap: 40,
    },
    productPresets: [
      {
        preset: { id: P3_PRESET_ID, name: "Chunk preset", system: true },
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
        preset: { id: "hybrid-preset", name: "Hybrid preset", system: false },
        indexRequirements: { requiredMaterializationStrategy: "HYBRID", requiresMetadataSupport: true },
        compatibility: {
          selectable: false,
          disabledReasonCode: "MATERIALIZATION_NOT_SUPPORTED",
          disabledReason: "Create or reindex the project with a compatible index profile.",
          indexRequirements: { requiredMaterializationStrategy: "HYBRID", requiresMetadataSupport: true },
          compatibleWithActiveIndex: false,
        },
      },
    ],
    experimentalPresets: [],
  },
  isLoading: false,
  isError: false,
  isSuccess: true,
  refetch: vi.fn(),
};

describe("CreateConversationPresetSelector", () => {
  beforeEach(() => {
    hooksMock.useProjectCompatiblePresets.mockReturnValue(compatibleCatalog);
  });

  it("requests project-scoped compatible presets for the given projectId", () => {
    renderSelector("project-42");
    expect(hooksMock.useProjectCompatiblePresets).toHaveBeenCalledWith("project-42", { enabled: true });
  });

  it("does not show Server default as a selectable option", () => {
    renderSelector();
    expect(screen.queryByRole("option", { name: /Server default/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Production assistant configuration/i })).not.toBeInTheDocument();
  });

  it("preselects a concrete compatible preset", () => {
    hooksMock.useProjectCompatiblePresets.mockReturnValue({
      ...compatibleCatalog,
      data: {
        ...compatibleCatalog.data,
        activeSnapshotCapabilities: {
          materializationStrategy: "HYBRID",
          supportsMetadata: true,
          embeddingModelId: "mxbai",
          chunkMaxChars: 400,
          chunkOverlap: 40,
        },
        productPresets: [
          {
            preset: { id: "cafe0001-0001-4001-8001-000000000003", name: "Demo_Best", system: true },
            indexRequirements: { requiredMaterializationStrategy: "HYBRID", requiresMetadataSupport: true },
            compatibility: {
              selectable: true,
              disabledReasonCode: null,
              disabledReason: null,
              indexRequirements: null,
              compatibleWithActiveIndex: true,
            },
          },
          ...compatibleCatalog.data.productPresets,
        ],
      },
    });
    renderSelector();
    const select = screen.getByTestId("chat-new-conversation-preset") as HTMLSelectElement;
    expect(select.value).toBe("cafe0001-0001-4001-8001-000000000003");
  });

  it("shows fallback hint when Demo_Best is incompatible", () => {
    hooksMock.useProjectCompatiblePresets.mockReturnValue({
      ...compatibleCatalog,
      data: {
        ...compatibleCatalog.data,
        productPresets: [
          {
            preset: { id: "cafe0001-0001-4001-8001-000000000003", name: "Demo_Best", system: true },
            indexRequirements: null,
            compatibility: {
              selectable: false,
              disabledReasonCode: "MATERIALIZATION_NOT_SUPPORTED",
              disabledReason: "Requires HYBRID",
              indexRequirements: null,
              compatibleWithActiveIndex: false,
            },
          },
          compatibleCatalog.data.productPresets[0]!,
        ],
      },
    });
    renderSelector();
    expect(screen.getByTestId("chat-new-conversation-default-preset-hint")).toBeInTheDocument();
  });

  it("does not render Show baseline presets toggle", () => {
    renderSelector();
    expect(screen.queryByTestId("chat-new-conversation-show-baseline")).not.toBeInTheDocument();
    expect(screen.queryByText(/Show baseline presets/i)).not.toBeInTheDocument();
  });

  it("hides metadata-required presets on CHUNK_LEVEL index without metadata support", () => {
    hooksMock.useProjectCompatiblePresets.mockReturnValue({
      ...compatibleCatalog,
      data: {
        ...compatibleCatalog.data,
        productPresets: [
          ...compatibleCatalog.data.productPresets,
          {
            preset: { id: "cafe0001-0001-4001-8001-000000000014", name: "Metadata preset", system: true },
            indexRequirements: { requiredMaterializationStrategy: "CHUNK_LEVEL", requiresMetadataSupport: true },
            compatibility: {
              selectable: true,
              disabledReasonCode: null,
              disabledReason: null,
              indexRequirements: null,
              compatibleWithActiveIndex: true,
            },
          },
        ],
      },
    });
    renderSelector();
    expect(screen.queryByRole("option", { name: /Metadata preset/i })).not.toBeInTheDocument();
  });

  it("STRUCTURED_SEARCH shows warning and only direct/non-retrieval presets by default", () => {
    hooksMock.useProjectCompatiblePresets.mockReturnValue({
      ...compatibleCatalog,
      data: {
        ...compatibleCatalog.data,
        activeSnapshotCapabilities: {
          materializationStrategy: "STRUCTURED_SEARCH",
          supportsMetadata: true,
          embeddingModelId: "mxbai",
          chunkMaxChars: 400,
          chunkOverlap: 40,
        },
        productPresets: [
          {
            preset: { id: "cafe0001-0001-4001-8001-000000000010", name: "Direct LLM", system: true },
            indexRequirements: null,
            compatibility: {
              selectable: true,
              disabledReasonCode: null,
              disabledReason: null,
              indexRequirements: null,
              compatibleWithActiveIndex: true,
            },
          },
          {
            preset: { id: "cafe0001-0001-4001-8001-000000000003", name: "Demo_Best", system: true },
            indexRequirements: { requiredMaterializationStrategy: "HYBRID", requiresMetadataSupport: true },
            compatibility: {
              selectable: false,
              disabledReasonCode: "STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED",
              disabledReason: "Structured-search projects do not support vector retrieval or retrieval-based RAG presets.",
              indexRequirements: null,
              compatibleWithActiveIndex: false,
            },
          },
        ],
        experimentalPresets: [],
      },
    });
    renderSelector();
    expect(screen.getByTestId("chat-new-conversation-preset-structured-search-warning")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /Direct LLM/i })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Demo_Best/i })).not.toBeInTheDocument();
  });

  it("shows only compatible presets by default", () => {
    renderSelector();
    expect(screen.getByRole("option", { name: /Chunk preset/i })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Hybrid preset/i })).not.toBeInTheDocument();
  });

  it("does not render preset options when compatibility API fails", () => {
    hooksMock.useProjectCompatiblePresets.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      isSuccess: false,
      refetch: vi.fn(),
    });
    renderSelector();
    expect(screen.getByTestId("chat-new-conversation-preset-load-error")).toBeInTheDocument();
    expect(screen.getByTestId("chat-new-conversation-preset")).toBeDisabled();
    expect(screen.queryByRole("option", { name: /Chunk preset/i })).not.toBeInTheDocument();
  });

  it("shows project-required error when projectId is blank", () => {
    renderSelector("   ");
    expect(screen.getByTestId("chat-new-conversation-preset-project-error")).toBeInTheDocument();
    expect(screen.getByTestId("chat-new-conversation-preset")).toBeDisabled();
  });

  it("shows incompatible presets disabled when advanced toggle is enabled", async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider>
          <CreateConversationPresetSelector
            projectId="p1"
            value=""
            onChange={vi.fn()}
            showIncompatiblePresets
            onShowIncompatiblePresetsChange={vi.fn()}
          />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    const option = screen.getByRole("option", { name: /Hybrid preset/i }) as HTMLOptionElement;
    expect(option.disabled).toBe(true);
    await user.click(screen.getByTestId("chat-new-conversation-show-incompatible"));
    expect(option.disabled).toBe(true);
  });
});
