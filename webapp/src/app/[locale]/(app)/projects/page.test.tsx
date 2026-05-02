import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useAppStore } from "@/store/app.store";
import ProjectsPage from "./page";

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useProjectList: vi.fn(() => ({
    data: {
      items: [
        {
          id: "p1",
          name: "Alpha",
          description: null,
          docCount: 0,
          convCount: 0,
          updatedAt: "",
          colorHex: null,
          iconKey: null,
        },
      ],
      total: 1,
    },
    isLoading: false,
    isError: false,
  })),
}));

vi.mock("@/features/projects/components/NewProjectDialog", () => ({
  NewProjectDialog: () => (
    <button type="button">
      new
    </button>
  ),
}));

vi.mock("@/features/projects/components/ProjectGrid", () => ({
  ProjectGrid: () => <div data-testid="project-grid-mock" />,
}));

describe("ProjectsPage", () => {
  const qc = createTestQueryClient();

  beforeEach(() => {
    useAppStore.setState({ activeProject: null });
  });

  it("shows a hint when projects exist but none is the active workspace", () => {
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectsPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("status")).toHaveTextContent(/Open project/i);
    expect(screen.getByTestId("project-grid-mock")).toBeInTheDocument();
  });

  it("hides the hint when a project is already active", () => {
    useAppStore.setState({ activeProject: { id: "p1", name: "Alpha" } });
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <ProjectsPage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
