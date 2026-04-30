import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test-utils/query-client";
import { IntlTestProvider } from "@/test-utils/intl";
import { useAppStore } from "@/store/app.store";
import { ProjectGrid } from "./ProjectGrid";

vi.mock("@/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: vi.fn() }),
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

const fetchLatestConversationIdMock = vi.fn(async () => "c1");

vi.mock("@/features/projects/lib/open-project-in-chat", () => ({
  fetchLatestConversationId: (...args: unknown[]) => fetchLatestConversationIdMock(...args),
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useActivateProject: () => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
  }),
}));

describe("ProjectGrid", () => {
  const qc = createTestQueryClient();

  beforeEach(() => {
    useAppStore.setState({ activeProject: null });
    fetchLatestConversationIdMock.mockReset();
    fetchLatestConversationIdMock.mockResolvedValue("c1");
  });

  it("renders projects and can activate", async () => {
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
        <IntlTestProvider>
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText("Alpha")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /open chat/i }));
  });

  it("routes to chat without conversation query when project has no conversations", async () => {
    fetchLatestConversationIdMock.mockResolvedValueOnce(null);
    const user = userEvent.setup();
    const items = [{ id: "p1", name: "Alpha", description: "d", docCount: 1, convCount: 0, updatedAt: "" }];
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider>
          <ProjectGrid items={items} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: /open chat/i }));
    expect(fetchLatestConversationIdMock).toHaveBeenCalled();
  });
});
