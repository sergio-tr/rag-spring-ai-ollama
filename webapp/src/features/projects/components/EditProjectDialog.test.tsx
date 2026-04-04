import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { ProjectSummary } from "@/types/api";
import { EditProjectDialog } from "./EditProjectDialog";

const patchHook = vi.hoisted(() => ({
  mutateAsync: vi.fn().mockResolvedValue(undefined),
  isPending: false,
  isError: false,
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  usePatchProject: () => ({
    mutateAsync: patchHook.mutateAsync,
    isPending: patchHook.isPending,
    isError: patchHook.isError,
  }),
}));

const sampleProject: ProjectSummary = {
  id: "proj-1",
  name: "Alpha",
  description: "Desc",
  docCount: 0,
  convCount: 0,
  updatedAt: "2025-01-01T00:00:00Z",
};

function renderEdit(project: ProjectSummary = sampleProject) {
  const qc = createTestQueryClient();
  return render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider>
        <EditProjectDialog project={project} />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

describe("EditProjectDialog", () => {
  beforeEach(() => {
    patchHook.mutateAsync.mockClear();
    patchHook.isError = false;
    patchHook.isPending = false;
  });

  it("submits patched name and description", async () => {
    const user = userEvent.setup();
    renderEdit();
    await user.click(screen.getByRole("button", { name: /^Edit$/i }));
    const nameInput = screen.getByLabelText(/^Name$/i);
    await user.clear(nameInput);
    await user.type(nameInput, "Beta");
    await user.click(screen.getByRole("button", { name: /^Save$/i }));
    await vi.waitFor(() =>
      expect(patchHook.mutateAsync).toHaveBeenCalledWith({
        id: "proj-1",
        name: "Beta",
        description: "Desc",
      }),
    );
  });

  it("closes without saving when Cancel is clicked", async () => {
    const user = userEvent.setup();
    renderEdit();
    await user.click(screen.getByRole("button", { name: /^Edit$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "Ignored");
    await user.click(screen.getByRole("button", { name: /^Cancel$/i }));
    expect(patchHook.mutateAsync).not.toHaveBeenCalled();
  });

  it("shows edit error when mutation failed", async () => {
    patchHook.isError = true;
    const user = userEvent.setup();
    renderEdit();
    await user.click(screen.getByRole("button", { name: /^Edit$/i }));
    expect(screen.getByRole("alert")).toHaveTextContent(/Could not update project/i);
  });
});
