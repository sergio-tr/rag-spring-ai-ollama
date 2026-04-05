import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test-utils/query-client";
import { IntlTestProvider } from "@/test-utils/intl";
import { useAppStore } from "@/store/app.store";
import { ProjectGrid } from "./ProjectGrid";

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

describe("ProjectGrid", () => {
  const qc = createTestQueryClient();

  beforeEach(() => {
    useAppStore.setState({ activeProject: null });
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
    await user.click(screen.getByRole("button", { name: /^Open$/i }));
  });
});
