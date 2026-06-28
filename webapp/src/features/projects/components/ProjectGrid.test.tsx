import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import type { QueryClient } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test-utils/query-client";
import { IntlTestProvider } from "@/test-utils/intl";
import { useAppStore } from "@/store/app.store";
import { ProjectGrid } from "./ProjectGrid";

const { pushMock, activateMocks, convMocks, deleteConversationMutateAsync } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  activateMocks: {
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue(undefined),
  },
  convMocks: {
    conversationsData: [] as { id: string; title: string; updatedAt: string }[],
    conversationsLoading: false,
    conversationsError: false,
    createMutateAsync: vi.fn(),
  },
  deleteConversationMutateAsync: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("@/navigation", () => ({
  useRouter: () => ({ push: pushMock, refresh: vi.fn(), replace: vi.fn() }),
}));

vi.mock("@/lib/api-client", () => ({
  apiFetch: vi.fn().mockResolvedValue({ activeProjectId: "p1" }),
  apiProductPath: (p: string) => p,
}));

vi.mock("@/features/projects/components/EditProjectDialog", () => ({
  EditProjectDialog: () => <span data-testid="edit-dlg">edit</span>,
}));
vi.mock("@/features/projects/components/DeleteProjectDialog", () => ({
  DeleteProjectDialog: () => <span data-testid="del-dlg">del</span>,
}));

const { fetchLatestConversationIdMock } = vi.hoisted(() => ({
  fetchLatestConversationIdMock: vi.fn<
    (queryClient: QueryClient, projectId: string) => Promise<string | null>
  >(async () => "c1"),
}));

vi.mock("@/features/projects/lib/open-project-in-chat", () => ({
  fetchLatestConversationId: fetchLatestConversationIdMock,
}));

vi.mock("@/features/chat/hooks/use-chat-presets-catalog", () => ({
  useChatPresetsCatalog: () => ({
    data: { productPresets: [], experimentalPresets: [] },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useConversations: (projectId?: string) => {
    if (!projectId) {
      return { data: undefined, isLoading: false, isError: false };
    }
    return {
      data: convMocks.conversationsData,
      isLoading: convMocks.conversationsLoading,
      isError: convMocks.conversationsError,
    };
  },
  useCreateConversation: () => ({
    mutateAsync: convMocks.createMutateAsync,
    isPending: false,
  }),
  useDeleteConversation: () => ({
    mutateAsync: deleteConversationMutateAsync,
    isPending: false,
    reset: vi.fn(),
  }),
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useActivateProject: () => ({
    mutate: activateMocks.mutate,
    mutateAsync: activateMocks.mutateAsync,
    isPending: false,
  }),
}));

vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useProjectDocuments: (projectId?: string) => {
    if (!projectId) {
      return { data: undefined, isLoading: false, isError: false };
    }
    return {
      data: [
        {
          id: "d-old",
          fileName: "older.doc",
          status: "READY",
          chunkCount: 0,
          errorMessage: null,
          uploadedAt: "2025-01-01T00:00:00Z",
          reindexedAt: null,
          corpusScope: "PROJECT_SHARED",
          conversationId: null,
          currentIndexSnapshotId: null,
          indexSignatureHash: null,
          storagePresent: true,
        },
        {
          id: "d-new",
          fileName: "newest.pdf",
          status: "READY",
          chunkCount: 1,
          errorMessage: null,
          uploadedAt: "2026-02-01T00:00:00Z",
          reindexedAt: null,
          corpusScope: "PROJECT_SHARED",
          conversationId: null,
          currentIndexSnapshotId: null,
          indexSignatureHash: null,
          storagePresent: true,
        },
      ],
      isLoading: false,
      isError: false,
    };
  },
}));

describe("ProjectGrid", () => {
  const qc = createTestQueryClient();

  beforeEach(() => {
    useAppStore.setState({ activeProject: null });
    fetchLatestConversationIdMock.mockReset();
    fetchLatestConversationIdMock.mockResolvedValue("c1");
    pushMock.mockReset();
    activateMocks.mutate.mockReset();
    activateMocks.mutateAsync.mockClear();
    convMocks.conversationsData = [
      { id: "c-old", title: "Older thread", updatedAt: "2026-01-01T00:00:00Z" },
      { id: "c-new", title: "Newer thread", updatedAt: "2026-02-01T00:00:00Z" },
    ];
    convMocks.conversationsLoading = false;
    convMocks.conversationsError = false;
    convMocks.createMutateAsync.mockReset();
    convMocks.createMutateAsync.mockResolvedValue({
      id: "c-created",
      title: "",
      updatedAt: "2026-03-01T00:00:00Z",
    });
    deleteConversationMutateAsync.mockReset();
    deleteConversationMutateAsync.mockResolvedValue(undefined);
  });

  it("primary Open project navigates to project-scoped chat with latest conversation", async () => {
    const user = userEvent.setup();
    const items = [
      {
        id: "p1",
        name: "Alpha",
        description: "d",
        docCount: 1,
        convCount: 2,
        updatedAt: "",
      },
    ];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText("Alpha")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Newer thread$/ })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /open project/i }));
    expect(pushMock).toHaveBeenCalledWith("/chat?projectId=p1&conversationId=c1");
  });

  it("routes to chat with projectId only when project has no conversations", async () => {
    fetchLatestConversationIdMock.mockResolvedValueOnce(null);
    const user = userEvent.setup();
    const items = [{ id: "p1", name: "Alpha", description: "d", docCount: 1, convCount: 0, updatedAt: "" }];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: /open project/i }));
    expect(fetchLatestConversationIdMock).toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/chat?projectId=p1");
  });

  it("Start first chat activates, creates conversation, and navigates to new chat", async () => {
    const user = userEvent.setup();
    const items = [{ id: "p1", name: "Alpha", description: "d", docCount: 0, convCount: 0, updatedAt: "" }];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: /start first chat/i }));
    const dlg = await screen.findByRole("dialog");
    await user.click(within(dlg).getByRole("button", { name: /^create conversation$/i }));
    expect(convMocks.createMutateAsync).toHaveBeenCalled();
    expect(activateMocks.mutateAsync).toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/chat?projectId=p1&conversationId=c-created");
  });

  it("latest chat link opens project-scoped chat for that conversation", async () => {
    const user = userEvent.setup();
    const items = [
      { id: "p1", name: "Alpha", description: "d", docCount: 0, convCount: 2, updatedAt: "" },
    ];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: /^Newer thread$/ }));
    expect(activateMocks.mutateAsync).toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/chat?projectId=p1&conversationId=c-new");
  });

  it("Browse chats activates and opens chat scoped by project only", async () => {
    const user = userEvent.setup();
    const items = [
      { id: "p1", name: "Alpha", description: "d", docCount: 1, convCount: 2, updatedAt: "" },
    ];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: /browse chats/i }));
    expect(activateMocks.mutateAsync).toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/chat?projectId=p1");
  });

  it("shows controlled error when conversation list fails", () => {
    convMocks.conversationsError = true;
    const items = [
      { id: "p1", name: "Alpha", description: "d", docCount: 0, convCount: 1, updatedAt: "" },
    ];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/could not load chats/i);
  });

  it("shows stale hint when summary claims chats but list is empty", () => {
    convMocks.conversationsData = [];
    const items = [
      { id: "p1", name: "Alpha", description: "d", docCount: 0, convCount: 1, updatedAt: "" },
    ];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText(/list came back empty/i)).toBeInTheDocument();
  });

  it("Set active only activates without navigating", async () => {
    const user = userEvent.setup();
    const items = [
      {
        id: "p1",
        name: "Alpha",
        description: "d",
        docCount: 1,
        convCount: 2,
        updatedAt: "",
      },
    ];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: /set active only/i }));
    expect(activateMocks.mutate).toHaveBeenCalledWith(
      expect.objectContaining({ id: "p1", name: "Alpha" }),
    );
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("shows latest document filename on the card when docCount > 0", () => {
    const items = [
      {
        id: "p1",
        name: "Alpha",
        description: "d",
        docCount: 2,
        convCount: 0,
        updatedAt: "",
      },
    ];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText(/newest\.pdf/i)).toBeInTheDocument();
  });

  it("Browse documents activates and opens scoped Documents route", async () => {
    const user = userEvent.setup();
    const items = [
      {
        id: "p1",
        name: "Alpha",
        description: "d",
        docCount: 1,
        convCount: 2,
        updatedAt: "",
      },
    ];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: /browse documents/i }));
    expect(pushMock).toHaveBeenCalledWith("/documents?projectId=p1");
  });

  it("shows Active on secondary control when project matches store", () => {
    useAppStore.setState({ activeProject: { id: "p1", name: "Alpha" } });
    const items = [
      {
        id: "p1",
        name: "Alpha",
        description: "d",
        docCount: 1,
        convCount: 2,
        updatedAt: "",
      },
    ];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("button", { name: /^active$/i })).toBeInTheDocument();
  });

  it("confirms delete for a listed chat on the project card", async () => {
    const user = userEvent.setup();
    const items = [
      { id: "p1", name: "Alpha", description: "d", docCount: 1, convCount: 2, updatedAt: "" },
    ];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: /Delete chat: Newer thread/i }));
    const dlg = await screen.findByRole("dialog");
    await user.click(within(dlg).getByRole("button", { name: /^Delete chat$/i }));
    expect(deleteConversationMutateAsync).toHaveBeenCalledWith("c-new");
  });
});
