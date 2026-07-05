import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { NewConversationDialog } from "./NewConversationDialog";
import { P0_PRESET_ID, P3_PRESET_ID } from "@/features/chat/lib/preset-product-selection";

const hooksMock = vi.hoisted(() => ({
  useProjectCompatiblePresets: vi.fn(),
  useProjectDocuments: vi.fn(),
  useCreateConversation: vi.fn(),
}));

vi.mock("@/features/chat/hooks/use-project-compatible-presets", () => ({
  useProjectCompatiblePresets: (...args: unknown[]) => hooksMock.useProjectCompatiblePresets(...args),
  buildProjectCompatiblePresetsPath: vi.fn(),
  projectCompatiblePresetsQueryKey: vi.fn(),
}));
vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useProjectDocuments: (...args: unknown[]) => hooksMock.useProjectDocuments(...args),
}));
vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useCreateConversation: (...args: unknown[]) => hooksMock.useCreateConversation(...args),
}));

function renderDialog(projectId = "p1", open = true) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const onCreated = vi.fn();
  render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider>
        <NewConversationDialog
          projectId={projectId}
          open={open}
          onOpenChange={vi.fn()}
          onCreated={onCreated}
        />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
  return { onCreated };
}

describe("NewConversationInitialPresetCompatibility", () => {
  beforeEach(() => {
    hooksMock.useProjectDocuments.mockReturnValue({ data: [], isLoading: false });
    hooksMock.useCreateConversation.mockReturnValue({
      isPending: false,
      mutateAsync: vi.fn(),
    });
  });

  it("loads compatible presets only when the dialog is open", () => {
    hooksMock.useProjectCompatiblePresets.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      isSuccess: false,
      refetch: vi.fn(),
    });
    renderDialog("p1", false);
    expect(hooksMock.useProjectCompatiblePresets).toHaveBeenCalledWith("p1", { enabled: false });
  });

  it("shows only compatible presets in the Initial preset field by default", () => {
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
            preset: { id: "hybrid-preset", name: "Hybrid preset", system: false },
            indexRequirements: null,
            compatibility: {
              selectable: false,
              disabledReasonCode: "MATERIALIZATION_NOT_SUPPORTED",
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
    expect(screen.getByLabelText(/Initial preset/i)).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /Chunk preset/i })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Hybrid preset/i })).not.toBeInTheDocument();
  });

  it("blocks create when compatibility API failed instead of falling back", () => {
    hooksMock.useProjectCompatiblePresets.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      isSuccess: false,
      refetch: vi.fn(),
    });

    renderDialog();
    expect(screen.getByTestId("chat-new-conversation-create")).toBeDisabled();
    expect(screen.getByTestId("chat-new-conversation-preset-load-error")).toBeInTheDocument();
  });

  it("updates visible presets when project changes", async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    hooksMock.useProjectCompatiblePresets.mockImplementation((projectId: string) => ({
      data: {
        projectId,
        effectiveEmbeddingModelId: null,
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
            preset: {
              id: projectId === "p1" ? P3_PRESET_ID : P0_PRESET_ID,
              name: `${projectId} preset`,
              system: false,
            },
            indexRequirements: null,
            compatibility: {
              selectable: true,
              disabledReasonCode: null,
              disabledReason: null,
              indexRequirements: null,
              compatibleWithActiveIndex: true,
            },
          },
        ],
        experimentalPresets: [],
      },
      isLoading: false,
      isError: false,
      isSuccess: true,
      refetch: vi.fn(),
    }));

    const { rerender } = render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider>
          <NewConversationDialog projectId="p1" open onOpenChange={vi.fn()} onCreated={vi.fn()} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("option", { name: /p1 preset/i })).toBeInTheDocument();

    rerender(
      <QueryClientProvider client={qc}>
        <IntlTestProvider>
          <NewConversationDialog projectId="p2" open onOpenChange={vi.fn()} onCreated={vi.fn()} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("option", { name: /p2 preset/i })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /p1 preset/i })).not.toBeInTheDocument();
  });
});
