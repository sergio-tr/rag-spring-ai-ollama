import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useAppStore } from "@/store/app.store";
import { AppSectionActions } from "./AppSectionActions";

const mockPathname = vi.fn(() => "/projects");
const mockPush = vi.fn();
const mockRefresh = vi.fn();
const mockSearchParams = vi.fn(() => new URLSearchParams());

vi.mock("@/navigation", () => ({
  usePathname: () => mockPathname(),
  useRouter: () => ({ push: mockPush, refresh: mockRefresh }),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => mockSearchParams(),
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useCreateProject: () => ({
    mutateAsync: vi.fn().mockResolvedValue({
      id: "p-new",
      name: "N",
      docCount: 0,
      convCount: 0,
      updatedAt: "",
    }),
    isPending: false,
    isError: false,
  }),
}));

const docsHooksMock = vi.hoisted(() => ({
  snapshot: {
    data: [] as { id: string }[],
    isLoading: false,
    isError: false,
  },
}));

const chatDeleteMocks = vi.hoisted(() => ({
  deleteMutateAsync: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("@/features/chat/hooks/use-conversations", () => ({
  useConversations: () => ({
    data: [
      {
        id: "c1",
        title: "Chat A",
        updatedAt: "",
        presetId: null,
        effectivePresetId: null,
        documentFilter: [],
      },
    ],
  }),
  useDeleteConversation: () => ({
    mutateAsync: chatDeleteMocks.deleteMutateAsync,
    isPending: false,
    reset: vi.fn(),
  }),
}));

vi.mock("@/features/documents/hooks/use-project-documents", () => ({
  useProjectDocuments: () => docsHooksMock.snapshot,
  useDeleteAllProjectDocuments: () => ({
    mutateAsync: vi.fn(),
    reset: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  }),
}));

vi.mock("@/features/documents/components/DeleteAllProjectDocumentsDialog", () => ({
  DeleteAllProjectDocumentsDialog: () => <div data-testid="delete-all-documents-dialog-mock" />,
}));

function renderActions() {
  const qc = createTestQueryClient();
  return {
    qc,
    ...render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <AppSectionActions />
        </IntlTestProvider>
      </QueryClientProvider>,
    ),
  };
}

describe("AppSectionActions", () => {
  beforeEach(() => {
    mockPathname.mockReturnValue("/projects");
    mockPush.mockClear();
    mockRefresh.mockClear();
    mockSearchParams.mockReturnValue(new URLSearchParams());
    useAppStore.setState({ activeProject: null });
    docsHooksMock.snapshot = { data: [], isLoading: false, isError: false };
    chatDeleteMocks.deleteMutateAsync.mockClear();
  });

  it("projects menu trigger has an accessible name", () => {
    renderActions();
    expect(screen.getByRole("button", { name: /projects actions/i })).toBeInTheDocument();
  });

  it("projects menu lists New project and opens the dialog", async () => {
    const user = userEvent.setup();
    renderActions();
    await user.click(screen.getByRole("button", { name: /projects actions/i }));
    await user.click(screen.getByRole("menuitem", { name: /^New project$/i }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /^New project$/i })).toBeInTheDocument();
  });

  it("projects menu keeps delete-all unavailable as a disabled item with explanation", async () => {
    const user = userEvent.setup();
    renderActions();
    await user.click(screen.getByRole("button", { name: /projects actions/i }));
    const deleteAll = screen.getByRole("menuitem", { name: /delete all projects/i });
    expect(deleteAll).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByText(/Not available — delete projects individually/i)).toBeInTheDocument();
  });

  it("documents menu disables refresh without an active project", async () => {
    mockPathname.mockReturnValue("/documents");
    const user = userEvent.setup();
    renderActions();
    await user.click(screen.getByRole("button", { name: /documents actions/i }));
    const refresh = screen.getByRole("menuitem", { name: /refresh document list/i });
    expect(refresh).toHaveAttribute("aria-disabled", "true");
  });

  it("documents refresh invalidates project documents when a project is active", async () => {
    mockPathname.mockReturnValue("/documents");
    useAppStore.setState({ activeProject: { id: "p1", name: "P1" } });
    const user = userEvent.setup();
    const { qc } = renderActions();
    const spy = vi.spyOn(qc, "invalidateQueries");
    await user.click(screen.getByRole("button", { name: /documents actions/i }));
    await user.click(screen.getByRole("menuitem", { name: /refresh document list/i }));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["project-documents", "p1"] });
  });

  it("documents menu disables delete-all when the project document list is empty", async () => {
    mockPathname.mockReturnValue("/documents");
    useAppStore.setState({ activeProject: { id: "p1", name: "P1" } });
    docsHooksMock.snapshot = { data: [], isLoading: false, isError: false };
    const user = userEvent.setup();
    renderActions();
    await user.click(screen.getByRole("button", { name: /documents actions/i }));
    expect(screen.getByRole("menuitem", { name: /delete all documents/i })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("documents menu enables delete-all when documents exist", async () => {
    mockPathname.mockReturnValue("/documents");
    useAppStore.setState({ activeProject: { id: "p1", name: "P1" } });
    docsHooksMock.snapshot = { data: [{ id: "d1" }], isLoading: false, isError: false };
    const user = userEvent.setup();
    renderActions();
    await user.click(screen.getByRole("button", { name: /documents actions/i }));
    const item = screen.getByRole("menuitem", { name: /delete all documents/i });
    expect(item).not.toHaveAttribute("aria-disabled", "true");
  });

  it("chat menu shows deferred placeholders except delete", async () => {
    mockPathname.mockReturnValue("/chat");
    mockSearchParams.mockReturnValue(new URLSearchParams({ conversationId: "c1", projectId: "p1" }));
    useAppStore.setState({ activeProject: { id: "p1", name: "P1" } });
    const user = userEvent.setup();
    renderActions();
    await user.click(screen.getByRole("button", { name: /chat actions/i }));
    expect(screen.getByRole("menuitem", { name: /move to another project/i })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByRole("menuitem", { name: /model/i })).toHaveAttribute("aria-disabled", "true");
    const deleteItem = screen.getByRole("menuitem", { name: /^Delete chat$/i });
    expect(deleteItem).not.toHaveAttribute("aria-disabled", "true");
    await user.click(screen.getByRole("menuitem", { name: /move to another project/i }));
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("chat delete confirmation cancel does not call API", async () => {
    mockPathname.mockReturnValue("/chat");
    mockSearchParams.mockReturnValue(new URLSearchParams({ conversationId: "c1", projectId: "p1" }));
    useAppStore.setState({ activeProject: { id: "p1", name: "P1" } });
    const user = userEvent.setup();
    renderActions();
    await user.click(screen.getByRole("button", { name: /chat actions/i }));
    await user.click(screen.getByRole("menuitem", { name: /^Delete chat$/i }));
    const dlg = await screen.findByRole("dialog");
    await user.click(within(dlg).getByRole("button", { name: /^Cancel$/i }));
    expect(chatDeleteMocks.deleteMutateAsync).not.toHaveBeenCalled();
  });

  it("chat delete confirmation calls API and clears conversation from route", async () => {
    mockPathname.mockReturnValue("/chat");
    mockSearchParams.mockReturnValue(new URLSearchParams({ conversationId: "c1", projectId: "p1" }));
    useAppStore.setState({ activeProject: { id: "p1", name: "P1" } });
    const user = userEvent.setup();
    renderActions();
    await user.click(screen.getByRole("button", { name: /chat actions/i }));
    await user.click(screen.getByRole("menuitem", { name: /^Delete chat$/i }));
    const dlg = await screen.findByRole("dialog");
    await user.click(within(dlg).getByRole("button", { name: /^Delete chat$/i }));
    expect(chatDeleteMocks.deleteMutateAsync).toHaveBeenCalledWith("c1");
    expect(mockPush).toHaveBeenCalledWith("/chat?projectId=p1");
  });

  it("settings menu calls router.refresh for reload", async () => {
    mockPathname.mockReturnValue("/settings/user");
    const user = userEvent.setup();
    renderActions();
    await user.click(screen.getByRole("button", { name: /settings actions/i }));
    await user.click(screen.getByRole("menuitem", { name: /reload settings view/i }));
    expect(mockRefresh).toHaveBeenCalledTimes(1);
  });

  it("settings data tab offers refresh usage data that invalidates summary queries", async () => {
    mockPathname.mockReturnValue("/settings/data");
    const user = userEvent.setup();
    const { qc } = renderActions();
    const spy = vi.spyOn(qc, "invalidateQueries");
    await user.click(screen.getByRole("button", { name: /settings actions/i }));
    await user.click(screen.getByRole("menuitem", { name: /refresh usage data/i }));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["settings", "me", "summary"] });
    expect(spy).toHaveBeenCalledWith({ queryKey: ["settings", "me", "documents", 0, 50] });
  });

  it("renders nothing on unknown sections", () => {
    mockPathname.mockReturnValue("/unknown-route");
    const { container } = renderActions();
    expect(container.querySelector('[aria-label*="actions"]')).toBeNull();
  });
});
