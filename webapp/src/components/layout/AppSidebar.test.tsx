import type { ReactNode } from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
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
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useProjectList: () => ({
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
      ],
      total: 1,
    },
    isLoading: false,
    isError: false,
  }),
  useActivateProject: () => ({
    mutateAsync: vi.fn(async () => {}),
  }),
}));

vi.mock("@/features/projects/components/NewProjectDialog", () => ({
  NewProjectDialog: ({ triggerClassName }: { triggerClassName?: string }) => (
    <button type="button" className={triggerClassName}>
      New project
    </button>
  ),
}));

vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useConversations: () => ({
    data: [{ id: "c1", title: "Chat One", updatedAt: "2026-01-01T00:00:00Z" }],
    isLoading: false,
    isError: false,
  }),
  useCreateConversation: () => ({ isPending: false, mutateAsync: vi.fn(async () => ({ id: "c1" })) }),
}));

describe("AppSidebar", () => {
  it("renders primary links and pinned settings", () => {
    render(
      <IntlTestProvider>
        <AppSidebar />
      </IntlTestProvider>,
    );
    expect(screen.getByLabelText("Main")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /projects/i })).toHaveAttribute("href", "/projects");
    expect(screen.getByRole("link", { name: /settings/i })).toHaveAttribute("href", "/settings");
    expect(screen.getByText(/rag console/i)).toBeInTheDocument();
  });

  it("hides Admin link for unknown/non-admin role", () => {
    render(
      <IntlTestProvider>
        <AppSidebar />
      </IntlTestProvider>,
    );
    expect(screen.queryByRole("link", { name: /^admin$/i })).not.toBeInTheDocument();
  });

  it("shows Admin link for ADMIN role", async () => {
    const { getStoredUserRole } = await import("@/lib/user-role");
    vi.mocked(getStoredUserRole).mockReturnValue("ADMIN");
    render(
      <IntlTestProvider>
        <AppSidebar />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("link", { name: /^admin$/i })).toHaveAttribute("href", "/admin");
  });

  it("supports collapsing projects and expanding a project node", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <AppSidebar />
      </IntlTestProvider>,
    );

    const projectsToggle = screen.getByRole("button", { name: /^projects$/i });
    expect(projectsToggle).toHaveAttribute("aria-expanded", "true");

    const projectRow = screen.getByRole("button", { name: /project one/i });
    expect(projectRow).toBeInTheDocument();

    const expandProject = screen.getByRole("button", { name: /expand chats for project one/i });
    expect(expandProject).toHaveAttribute("aria-expanded", "false");
    await user.click(expandProject);
    expect(expandProject).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("button", { name: /chat one/i })).toBeInTheDocument();

    await user.click(projectsToggle);
    expect(projectsToggle).toHaveAttribute("aria-expanded", "false");
  });
});
