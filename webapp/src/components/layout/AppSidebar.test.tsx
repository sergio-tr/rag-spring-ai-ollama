import type { ReactNode } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { ProjectSummary } from "@/types/api";
import { useAppStore } from "@/store/app.store";
import { AppSidebar } from "./AppSidebar";

vi.mock("@/lib/user-role", () => ({
  getStoredUserRole: vi.fn(() => null),
  setStoredUserRole: vi.fn(),
}));

vi.mock("@/navigation", () => ({
  Link: ({ href, children, className }: { href: string; children: ReactNode; className?: string }) => (
    <a href={href} className={className}>
      {children}
    </a>
  ),
  usePathname: () => "/projects",
  useRouter: () => ({ push: pushMock, refresh: vi.fn(), replace: vi.fn() }),
}));

const pushMock = vi.fn();

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));

const fetchLatestConversationIdMock = vi.fn(async () => "c-open");

vi.mock("@/features/projects/lib/open-project-in-chat", () => ({
  fetchLatestConversationId: (...args: unknown[]) => fetchLatestConversationIdMock(...args),
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
    localStorage.removeItem("rag-sidebar");
    useAppStore.setState({ activeProject: null });
  });

  it("renders primary links and pinned settings", () => {
    render(<AppSidebar />, { wrapper: Wrapper });
    expect(screen.getByLabelText("Main")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /projects/i })).toHaveAttribute("href", "/projects");
    expect(screen.getByRole("link", { name: /settings/i })).toHaveAttribute("href", "/settings");
    expect(screen.getByText(/rag console/i)).toBeInTheDocument();
  });

  it("hides Admin link for unknown/non-admin role", () => {
    render(<AppSidebar />, { wrapper: Wrapper });
    expect(screen.queryByRole("link", { name: /^admin$/i })).not.toBeInTheDocument();
  });

  it("shows Admin link for ADMIN role", async () => {
    const { getStoredUserRole } = await import("@/lib/user-role");
    vi.mocked(getStoredUserRole).mockReturnValue("ADMIN");
    render(<AppSidebar />, { wrapper: Wrapper });
    expect(screen.getByRole("link", { name: /^admin$/i })).toHaveAttribute("href", "/admin");
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
    expect(screen.getByRole("button", { name: /chat one/i })).toBeInTheDocument();

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
    expect(screen.getByRole("link", { name: /view all projects/i })).toHaveAttribute("href", "/projects");
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
    expect(mockCreateConversation.mutateAsync).toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/chat?conversationId=c42");
  });

  it("searches chats across projects and activates when selecting a different project", async () => {
    const user = userEvent.setup();
    render(<AppSidebar />, { wrapper: Wrapper });

    await user.click(screen.getByRole("button", { name: /search chat/i }));
    await user.type(screen.getByPlaceholderText(/chat title/i), "budget");

    const match = await screen.findByRole("button", { name: /budget chat/i });
    await user.click(match);
    expect(activateProjectMutateAsync).toHaveBeenCalledWith({ id: "p2", name: "Project Two" });
    expect(pushMock).toHaveBeenCalledWith("/chat?conversationId=c2");
  });

  it("opens project without conversationId when project has no chats", async () => {
    fetchLatestConversationIdMock.mockResolvedValueOnce(null);
    const user = userEvent.setup();
    render(<AppSidebar />, { wrapper: Wrapper });
    await user.click(screen.getByRole("button", { name: /^project one$/i }));
    expect(pushMock).toHaveBeenCalledWith("/chat");
  });

  it("disables new conversation CTA while create is pending", () => {
    useAppStore.setState({ activeProject: { id: "p1", name: "Project One" } });
    mockCreateConversation.isPending = true;
    render(<AppSidebar />, { wrapper: Wrapper });
    expect(screen.getByRole("button", { name: /new conversation/i })).toBeDisabled();
  });
});
