import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { useAppStore } from "@/store/app.store";
import { AppContextBreadcrumb } from "./AppContextBreadcrumb";
import * as convHooks from "@/features/chat/hooks/use-conversations";

const mockPathname = vi.fn(() => "/chat");
const mockSearchParams = vi.fn(() => new URLSearchParams());

vi.mock("@/navigation", () => ({
  usePathname: () => mockPathname(),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => mockSearchParams(),
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useProjectList: vi.fn(() => ({
    data: {
      items: [
        {
          id: "p1",
          name: "Resolved Project",
          docCount: 0,
          convCount: 1,
          updatedAt: "2026-01-01T00:00:00Z",
          iconKey: "folder",
          colorHex: "#112233",
        },
      ],
      total: 1,
    },
    isLoading: false,
  })),
}));

vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useConversations: vi.fn(),
}));

describe("AppContextBreadcrumb", () => {
  beforeEach(() => {
    mockPathname.mockReturnValue("/chat");
    mockSearchParams.mockReturnValue(new URLSearchParams());
    useAppStore.setState({ activeProject: null });
    vi.mocked(convHooks.useConversations).mockReturnValue({
      data: [{ id: "c99", title: "Budget discussion", updatedAt: "2026-01-01T00:00:00Z" }],
      isLoading: false,
      isError: false,
    } as ReturnType<typeof convHooks.useConversations>);
  });

  it("shows no project selected when store has no active project", () => {
    mockPathname.mockReturnValue("/documents");
    render(
      <IntlTestProvider locale="en">
        <AppContextBreadcrumb />
      </IntlTestProvider>,
    );
    expect(screen.getByText(/no project selected/i)).toBeInTheDocument();
    expect(screen.getByText(/^documents$/i)).toBeInTheDocument();
  });

  it("shows resolved project name and chat title when conversation id present", () => {
    useAppStore.setState({
      activeProject: { id: "p1", name: "Stale Name", iconKey: "rocket", colorHex: null },
    });
    mockSearchParams.mockReturnValue(new URLSearchParams({ conversationId: "c99" }));
    render(
      <IntlTestProvider locale="en">
        <AppContextBreadcrumb />
      </IntlTestProvider>,
    );
    expect(screen.getByText("Resolved Project")).toBeInTheDocument();
    expect(screen.getByText(/^chat$/i)).toBeInTheDocument();
    expect(screen.getByText("Budget discussion")).toBeInTheDocument();
  });

  it("shows settings tab segment on settings routes", () => {
    mockPathname.mockReturnValue("/settings/user");
    useAppStore.setState({ activeProject: { id: "p1", name: "P", iconKey: null, colorHex: null } });
    render(
      <IntlTestProvider locale="en">
        <AppContextBreadcrumb />
      </IntlTestProvider>,
    );
    expect(screen.getByText(/user config/i)).toBeInTheDocument();
  });

  it("exposes breadcrumb nav landmark for assistive tech", () => {
    mockPathname.mockReturnValue("/projects");
    render(
      <IntlTestProvider locale="en">
        <AppContextBreadcrumb />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("navigation", { name: /current location/i })).toBeInTheDocument();
  });

  it("uses untitled chat fallback when conversation title missing", () => {
    useAppStore.setState({ activeProject: { id: "p1", name: "P", iconKey: null, colorHex: null } });
    mockSearchParams.mockReturnValue(new URLSearchParams({ conversationId: "c99" }));
    vi.mocked(convHooks.useConversations).mockReturnValueOnce({
      data: [{ id: "c99", title: "   ", updatedAt: "" }],
      isLoading: false,
      isError: false,
    } as ReturnType<typeof convHooks.useConversations>);
    render(
      <IntlTestProvider locale="en">
        <AppContextBreadcrumb />
      </IntlTestProvider>,
    );
    expect(screen.getByText(/untitled chat/i)).toBeInTheDocument();
  });
});
