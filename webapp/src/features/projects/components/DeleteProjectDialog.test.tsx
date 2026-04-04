import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { ProjectSummary } from "@/types/api";
import { DeleteProjectDialog } from "./DeleteProjectDialog";

const deleteHook = vi.hoisted(() => ({
  mutateAsync: vi.fn().mockResolvedValue(undefined),
  isPending: false,
  isError: false,
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useDeleteProject: () => ({
    mutateAsync: deleteHook.mutateAsync,
    isPending: deleteHook.isPending,
    isError: deleteHook.isError,
  }),
}));

const sampleProject: ProjectSummary = {
  id: "proj-del",
  name: "To remove",
  description: null,
  docCount: 1,
  convCount: 0,
  updatedAt: "2025-01-01T00:00:00Z",
};

function renderDelete(project: ProjectSummary = sampleProject) {
  const qc = createTestQueryClient();
  return render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider>
        <DeleteProjectDialog project={project} />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

describe("DeleteProjectDialog", () => {
  beforeEach(() => {
    deleteHook.mutateAsync.mockClear();
    deleteHook.isError = false;
    deleteHook.isPending = false;
  });

  it("deletes project when confirmed", async () => {
    const user = userEvent.setup();
    renderDelete();
    await user.click(screen.getByRole("button", { name: /^Delete$/i }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: /^Delete$/i }));
    await vi.waitFor(() => expect(deleteHook.mutateAsync).toHaveBeenCalledWith("proj-del"));
  });

  it("closes without deleting when Cancel is clicked", async () => {
    const user = userEvent.setup();
    renderDelete();
    await user.click(screen.getByRole("button", { name: /^Delete$/i }));
    await user.click(screen.getByRole("button", { name: /^Cancel$/i }));
    expect(deleteHook.mutateAsync).not.toHaveBeenCalled();
  });

  it("shows delete error when mutation failed", async () => {
    deleteHook.isError = true;
    const user = userEvent.setup();
    renderDelete();
    await user.click(screen.getByRole("button", { name: /^Delete$/i }));
    expect(screen.getByRole("alert")).toHaveTextContent(/Could not delete project/i);
  });
});
