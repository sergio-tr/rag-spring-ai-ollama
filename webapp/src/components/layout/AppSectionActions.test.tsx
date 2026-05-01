import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
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

  it("chat menu shows deferred placeholders and does not navigate when toggled", async () => {
    mockPathname.mockReturnValue("/chat");
    mockSearchParams.mockReturnValue(new URLSearchParams({ conversationId: "c1" }));
    const user = userEvent.setup();
    renderActions();
    await user.click(screen.getByRole("button", { name: /chat actions/i }));
    expect(screen.getByRole("menuitem", { name: /move to another project/i })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByRole("menuitem", { name: /model/i })).toHaveAttribute("aria-disabled", "true");
    await user.click(screen.getByRole("menuitem", { name: /move to another project/i }));
    expect(mockPush).not.toHaveBeenCalled();
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
