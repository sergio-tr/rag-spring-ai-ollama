import type { ReactNode } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import type { QueryClient } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { ProjectSummary } from "@/types/api";
import { useAppStore } from "@/store/app.store";
import { SETTINGS_LAST_PATH_STORAGE_KEY } from "@/features/settings/lib/settings-last-path";
import { AppSidebar } from "./AppSidebar";

vi.mock("@/lib/user-role", () => ({
  getStoredUserRole: vi.fn(() => null),
  setStoredUserRole: vi.fn(),
}));

const apiFetchMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiFetch: (...args: unknown[]) => apiFetchMock(...args),
}));

const { pushMock, replaceMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  replaceMock: vi.fn(),
}));

vi.mock("@/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/navigation")>();
  return {
    ...actual,
    usePathname: () => "/projects",
    useRouter: () => ({ push: pushMock, refresh: vi.fn(), replace: replaceMock }),
  };
});

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));

const { fetchLatestConversationIdMock } = vi.hoisted(() => ({
  fetchLatestConversationIdMock: vi.fn<
    (queryClient: QueryClient, projectId: string) => Promise<string | null>
  >(async () => "c-open"),
}));

vi.mock("@/features/projects/lib/open-project-in-chat", () => ({
  fetchLatestConversationId: fetchLatestConversationIdMock,
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useProjectList: () => mockProjectsState,
  useActivateProject: () => ({
    mutateAsync: activateProjectMutateAsync,
  }),
}));

const activateProjectMutateAsync = vi.fn(async () => {});

const mockProjectsState: {
  data: { items: ProjectSummary[]; total: number } | null;
  isLoading: boolean;
  isError: boolean;
} = {
  data: {
    items: [
      {
        id: "p1",
        name: "Project One",
        docCount: 0,
        convCount: 0,
        updatedAt: "2026-01-01T00:00:00Z",
        colorHex: "#ff0000",
        iconKey: "folder",
      },
      {
        id: "p2",
        name: "Project Two",
        docCount: 0,
        convCount: 0,
        updatedAt: "2026-01-01T00:00:00Z",
        colorHex: null,
        iconKey: "rocket",
      },
      {
        id: "p3",
        name: "Project Three",
        docCount: 0,
        convCount: 0,
        updatedAt: "2026-01-01T00:00:00Z",
        colorHex: "#00ff00",
        iconKey: "shield",
      },
    ],
    total: 3,
  },
  isLoading: false,
  isError: false,
};

vi.mock("@/features/projects/components/NewProjectDialog", () => ({
  NewProjectDialog: ({ triggerClassName }: { triggerClassName?: string }) => (
    <button type="button" className={triggerClassName}>
      New project
    </button>
  ),
}));

vi.mock("@/features/chat/hooks/use-project-compatible-presets", async () => {
  const { compatiblePresetsQueryMock } = await import("@/test-utils/compatible-presets-mock");
  return {
    useProjectCompatiblePresets: () => compatiblePresetsQueryMock,
  };
});

vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", async () => {
  const { effectiveEmbeddingDefaultsMock } = await import("@/test-utils/compatible-presets-mock");
  return {
    useMeEffectiveEmbeddingDefaults: () => effectiveEmbeddingDefaultsMock,
  };
});

vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useProjectDocuments: () => ({
    data: [],
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useConversations: (projectId?: string) => ({
    data:
      projectId === "p2"
        ? [{ id: "c2", title: "Budget Chat", updatedAt: "2026-01-01T00:00:00Z" }]
        : [{ id: "c1", title: "Chat One", updatedAt: "2026-01-01T00:00:00Z" }],
    isLoading: false,
    isError: false,
  }),
  useCreateConversation: () => mockCreateConversation,
  useDeleteConversation: () => ({
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
    reset: vi.fn(),
  }),
}));

const mockCreateConversation: { isPending: boolean; mutateAsync: ReturnType<typeof vi.fn> } = {
  isPending: false,
  mutateAsync: vi.fn(async () => ({ id: "c1" })),
};

describe("AppSidebar", () => {
  const queryClient = createTestQueryClient();

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <IntlTestProvider>{children}</IntlTestProvider>
      </QueryClientProvider>
    );
  }

  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    activateProjectMutateAsync.mockClear();
    mockProjectsState.data = {
      items: [
        {
          id: "p1",
          name: "Project One",
          docCount: 0,
          convCount: 0,
          updatedAt: "2026-01-01T00:00:00Z",
          colorHex: "#ff0000",
          iconKey: "folder",
        },
        {
          id: "p2",
          name: "Project Two",
          docCount: 0,
          convCount: 0,
          updatedAt: "2026-01-01T00:00:00Z",
          colorHex: null,
          iconKey: "rocket",
        },
        {
          id: "p3",
          name: "Project Three",
          docCount: 0,
          convCount: 0,
          updatedAt: "2026-01-01T00:00:00Z",
          colorHex: "#00ff00",
          iconKey: "shield",
        },
      ],
      total: 3,
    };
    mockProjectsState.isLoading = false;
    mockProjectsState.isError = false;
    mockCreateConversation.isPending = false;
    mockCreateConversation.mutateAsync.mockClear();
    fetchLatestConversationIdMock.mockReset();
    fetchLatestConversationIdMock.mockResolvedValue("c-open");
    apiFetchMock.mockReset();
    // Default to a rejected auth probe to avoid async state updates
    // from the role bootstrap effect in tests that do not care about it.
    apiFetchMock.mockRejectedValue(new Error("auth unavailable"));
    localStorage.removeItem("rag-sidebar");
    sessionStorage.removeItem(SETTINGS_LAST_PATH_STORAGE_KEY);
    useAppStore.setState({ activeProject: null });
  });

  it("renders primary links and pinned settings", () => {
    render(<AppSidebar />, { wrapper: Wrapper });
    expect(screen.getByLabelText("Main")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /projects/i })).toHaveAttribute("href", "/en/projects");
    expect(screen.getByRole("link", { name: /settings/i })).toHaveAttribute("href", "/en/settings");
    expect(screen.getByText(/rag console/i)).toBeInTheDocument();
  });

  it("restores Settings sidebar href from sessionStorage after hydrate", async () => {
    sessionStorage.setItem(SETTINGS_LAST_PATH_STORAGE_KEY, "/settings/account");
    render(<AppSidebar />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(screen.getByRole("link", { name: /settings/i })).toHaveAttribute("href", "/en/settings/account");
    });
  });

  it("hides projects tree and actions when desktop rail is collapsed", () => {
    render(<AppSidebar variant="desktop" railCollapsed onToggleRailCollapsed={vi.fn()} />, {
      wrapper: Wrapper,
    });
    expect(screen.queryByRole("button", { name: /^projects$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /new project/i })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Main")).toBeInTheDocument();
    const brand = screen.getByRole("link", { name: /rag console/i });
    expect(brand.querySelector("img")).toHaveAttribute("src", "/logo.svg");
  });

  it("invokes onToggleRailCollapsed from desktop chrome toggle", async () => {
    const user = userEvent.setup();
    const onToggleRailCollapsed = vi.fn();
    render(
      <AppSidebar variant="desktop" railCollapsed={false} onToggleRailCollapsed={onToggleRailCollapsed} />,
      { wrapper: Wrapper },
    );
    await user.click(screen.getByRole("button", { name: /collapse sidebar/i }));
    expect(onToggleRailCollapsed).toHaveBeenCalledTimes(1);
  });

  it("invokes onSignOut from sidebar footer when provided", async () => {
    const user = userEvent.setup();
    const onSignOut = vi.fn();
    render(<AppSidebar onSignOut={onSignOut} />, { wrapper: Wrapper });
    await user.click(screen.getByRole("button", { name: /^sign out$/i }));
    expect(onSignOut).toHaveBeenCalledTimes(1);
  });

  it("hides Admin link for unknown/non-admin role", () => {
    render(<AppSidebar />, { wrapper: Wrapper });
    expect(screen.queryByRole("link", { name: /^admin$/i })).not.toBeInTheDocument();
  });

  it("shows Admin link for ADMIN role", async () => {
    const { getStoredUserRole } = await import("@/lib/user-role");
    vi.mocked(getStoredUserRole).mockReturnValue("ADMIN");
    render(<AppSidebar />, { wrapper: Wrapper });
    expect(screen.getByRole("link", { name: /^admin$/i })).toHaveAttribute("href", "/en/admin");
  });

  it("shows Admin link when auth me endpoint returns ADMIN", async () => {
    apiFetchMock.mockResolvedValueOnce({ roleName: "ADMIN" });
    render(<AppSidebar />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(screen.getByRole("link", { name: /^admin$/i })).toHaveAttribute("href", "/en/admin");
    });
  });

  it("supports collapsing projects and expanding a project node", async () => {
    const user = userEvent.setup();
    render(<AppSidebar />, { wrapper: Wrapper });

    const projectsToggle = screen.getByRole("button", { name: /^projects$/i });
    expect(projectsToggle).toHaveAttribute("aria-expanded", "true");

    const projectRow = screen.getByRole("button", { name: /^project one$/i });
    expect(projectRow).toBeInTheDocument();

    const expandProject = screen.getByRole("button", { name: /expand chats for project one/i });
    expect(expandProject).toHaveAttribute("aria-expanded", "false");
    await user.click(expandProject);
    expect(expandProject).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("button", { name: /^chat one$/i })).toBeInTheDocument();

    await user.click(projectsToggle);
    expect(projectsToggle).toHaveAttribute("aria-expanded", "false");
  });

  it("shows loading and error states for project list", () => {
    mockProjectsState.data = null;
    mockProjectsState.isLoading = true;
    render(<AppSidebar />, { wrapper: Wrapper });
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it("shows project list error message when hook errors", () => {
    mockProjectsState.data = null;
    mockProjectsState.isLoading = false;
    mockProjectsState.isError = true;
    render(<AppSidebar />, { wrapper: Wrapper });
    expect(screen.getByText(/failed to load projects/i)).toBeInTheDocument();
  });

  it("shows a link to view all projects when the list is truncated", () => {
    mockProjectsState.data = {
      items: mockProjectsState.data?.items ?? [],
      total: 100,
    };
    render(<AppSidebar />, { wrapper: Wrapper });
    expect(screen.getByRole("link", { name: /view all projects/i })).toHaveAttribute("href", "/en/projects");
  });

  it("opens the search dialog", async () => {
    const user = userEvent.setup();
    render(<AppSidebar />, { wrapper: Wrapper });
    await user.click(screen.getByRole("button", { name: /search chat/i }));
    expect(screen.getByRole("heading", { name: /search chat/i })).toBeInTheDocument();
  });

  it("restores persisted collapsed/expanded state", () => {
    localStorage.setItem("rag-sidebar", JSON.stringify({ projectsCollapsed: true, expandedProjectIds: ["p1"] }));
    render(<AppSidebar />, { wrapper: Wrapper });
    const projectsToggle = screen.getByRole("button", { name: /^projects$/i });
    expect(projectsToggle).toHaveAttribute("aria-expanded", "false");
  });

  it("ignores invalid persisted sidebar JSON", () => {
    localStorage.setItem("rag-sidebar", "{not-json");
    render(<AppSidebar />, { wrapper: Wrapper });
    const projectsToggle = screen.getByRole("button", { name: /^projects$/i });
    expect(projectsToggle).toHaveAttribute("aria-expanded", "true");
  });

  it("creates a new conversation when active project exists", async () => {
    useAppStore.setState({ activeProject: { id: "p1", name: "Project One" } });
    mockCreateConversation.mutateAsync.mockResolvedValueOnce({ id: "c42" });

    const user = userEvent.setup();
    render(<AppSidebar />, { wrapper: Wrapper });
    await user.click(screen.getByRole("button", { name: /new conversation/i }));
    const dlg = await screen.findByRole("dialog");
    await user.click(within(dlg).getByRole("button", { name: /^create conversation$/i }));
    expect(mockCreateConversation.mutateAsync).toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/chat?projectId=p1&conversationId=c42");
  });

  it("searches chats across projects and activates when selecting a different project", async () => {
    const user = userEvent.setup();
    render(<AppSidebar />, { wrapper: Wrapper });

    await user.click(screen.getByRole("button", { name: /search chat/i }));
    await user.type(screen.getByPlaceholderText(/chat title/i), "budget");

    const match = await screen.findByRole("button", { name: /^budget chat open$/i });
    await user.click(match);
    expect(activateProjectMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ id: "p2", name: "Project Two" }),
    );
    expect(pushMock).toHaveBeenCalledWith("/chat?projectId=p2&conversationId=c2");
  });

  it("searches chats without re-activating when project is already active", async () => {
    useAppStore.setState({ activeProject: { id: "p2", name: "Project Two" } });
    const user = userEvent.setup();
    render(<AppSidebar />, { wrapper: Wrapper });

    await user.click(screen.getByRole("button", { name: /search chat/i }));
    await user.type(screen.getByPlaceholderText(/chat title/i), "budget");

    const match = await screen.findByRole("button", { name: /^budget chat open$/i });
    await user.click(match);

    expect(activateProjectMutateAsync).not.toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/chat?projectId=p2&conversationId=c2");
  });

  it("opens project without conversationId when project has no chats", async () => {
    fetchLatestConversationIdMock.mockResolvedValueOnce(null);
    const user = userEvent.setup();
    render(<AppSidebar />, { wrapper: Wrapper });
    await user.click(screen.getByRole("button", { name: /^project one$/i }));
    expect(pushMock).toHaveBeenCalledWith("/chat?projectId=p1");
  });

  it("main nav Chat link includes active projectId when a project is selected", () => {
    useAppStore.setState({ activeProject: { id: "p1", name: "Project One" } });
    render(<AppSidebar />, { wrapper: Wrapper });
    const chatLink = screen.getByRole("link", { name: /^chat$/i });
    expect(chatLink).toHaveAttribute("href", "/en/chat?projectId=p1");
  });

  it("main nav Chat link goes to projects when no active project", () => {
    useAppStore.setState({ activeProject: null });
    render(<AppSidebar />, { wrapper: Wrapper });
    const chatLink = screen.getByRole("link", { name: /^chat$/i });
    expect(chatLink).toHaveAttribute("href", "/en/projects");
  });

  it("clears stale active project when it is not in the current list", async () => {
    useAppStore.setState({ activeProject: { id: "missing", name: "Old Project" } });
    render(<AppSidebar />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(useAppStore.getState().activeProject).toBeNull();
    });
  });

  it("activates a project when selecting a conversation from a collapsed inactive project", async () => {
    useAppStore.setState({ activeProject: { id: "p2", name: "Project Two" } });
    const user = userEvent.setup();
    render(<AppSidebar />, { wrapper: Wrapper });

    const expandProjectOne = screen.getByRole("button", { name: /expand chats for project one/i });
    await user.click(expandProjectOne);
    await user.click(screen.getByRole("button", { name: /^chat one$/i }));

    expect(activateProjectMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ id: "p1", name: "Project One" }),
    );
    expect(pushMock).toHaveBeenCalledWith("/chat?projectId=p1&conversationId=c1");
  });

  it("disables new conversation CTA while create is pending", () => {
    useAppStore.setState({ activeProject: { id: "p1", name: "Project One" } });
    mockCreateConversation.isPending = true;
    render(<AppSidebar />, { wrapper: Wrapper });
    expect(screen.getByRole("button", { name: /new conversation/i })).toBeDisabled();
  });
});
