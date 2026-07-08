import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { NewConversationDialog } from "./NewConversationDialog";
import { P3_PRESET_ID } from "@/features/chat/lib/preset-product-selection";

const hooksMock = vi.hoisted(() => ({
  useProjectCompatiblePresets: vi.fn(),
  useProjectDocuments: vi.fn(),
  useCreateConversation: vi.fn(),
}));

vi.mock("@/features/chat/hooks/use-project-compatible-presets", () => ({
  useProjectCompatiblePresets: (...args: unknown[]) => hooksMock.useProjectCompatiblePresets(...args),
}));
vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useProjectDocuments: (...args: unknown[]) => hooksMock.useProjectDocuments(...args),
}));
vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useCreateConversation: (...args: unknown[]) => hooksMock.useCreateConversation(...args),
}));

function renderDialog(open = true) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const onCreated = vi.fn();
  render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider>
        <NewConversationDialog
          projectId="p1"
          open={open}
          onOpenChange={vi.fn()}
          onCreated={onCreated}
        />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
  return { onCreated };
}

describe("NewConversationPresetSelectorCompatibility", () => {
  beforeEach(() => {
    hooksMock.useProjectDocuments.mockReturnValue({ data: [], isLoading: false });
    hooksMock.useCreateConversation.mockReturnValue({
      isPending: false,
      mutateAsync: vi.fn(),
    });
  });

  it("shows only compatible presets by default", () => {
    hooksMock.useProjectCompatiblePresets.mockReturnValue({
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
    });

    renderDialog();
    expect(screen.getByRole("option", { name: /Chunk preset/i })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Hybrid preset/i })).not.toBeInTheDocument();
  });

  it("shows incompatible presets disabled when advanced toggle is enabled", async () => {
    hooksMock.useProjectCompatiblePresets.mockReturnValue({
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
          chunkOverlap: null,
        },
        productPresets: [
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
    });

    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByTestId("chat-new-conversation-show-incompatible"));
    const option = screen.getByRole("option", { name: /Hybrid preset/i }) as HTMLOptionElement;
    expect(option.disabled).toBe(true);
  });

  it("shows empty state when project has no index and no compatible presets", () => {
    hooksMock.useProjectCompatiblePresets.mockReturnValue({
      data: {
        projectId: "p1",
        effectiveEmbeddingModelId: null,
        hasActiveIndex: false,
        readyDocumentCount: 0,
        activeSnapshotCapabilities: null,
        productPresets: [
          {
            preset: { id: "retrieval-preset", name: "Retrieval preset", system: false },
            indexRequirements: { requiredMaterializationStrategy: "CHUNK_LEVEL", requiresMetadataSupport: false },
            compatibility: {
              selectable: false,
              disabledReasonCode: "NO_ACTIVE_INDEX",
              disabledReason: "Create or reindex the project with a compatible index profile.",
              indexRequirements: null,
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
    });

    renderDialog();
    expect(screen.getByTestId("chat-new-conversation-preset-empty")).toHaveTextContent(
      /No indexed documents yet/i,
    );
  });
});
