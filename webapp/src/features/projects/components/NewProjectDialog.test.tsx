import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import { NewProjectDialog } from "./NewProjectDialog";

const createHook = vi.hoisted(() => ({
  mutateAsync: vi.fn().mockResolvedValue({
    project: {
      id: "p1",
      name: "N",
      docCount: 0,
      convCount: 0,
      updatedAt: "",
    },
  }),
  reset: vi.fn(),
  isPending: false,
  isError: false,
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useCreateProject: () => ({
    mutateAsync: createHook.mutateAsync,
    reset: createHook.reset,
    isPending: createHook.isPending,
    isError: createHook.isError,
  }),
}));

function renderDialog() {
  const qc = createTestQueryClient();
  return render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider>
        <NewProjectDialog />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

describe("NewProjectDialog", () => {
  beforeEach(() => {
    createHook.mutateAsync.mockClear();
    createHook.isError = false;
    createHook.isPending = false;
  });

  it("creates a project from the dialog form", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "My project");
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    await vi.waitFor(() =>
      expect(createHook.mutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          name: "My project",
          initialIndexProfile: expect.objectContaining({
            materializationStrategy: "CHUNK_LEVEL",
            metadataEnabled: false,
          }),
        }),
      ),
    );
  });

  it("shows validation error when name is empty", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    const alert = screen.getByRole("alert");
    expect(alert.textContent).toMatch(/Too small|required|at least 1|>=1/i);
  });

  it("shows validation error when name exceeds max length", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "a".repeat(121));
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    const alert = screen.getByRole("alert");
    expect(alert.textContent).toMatch(/120|too many|maximum/i);
  });

  it("closes without creating when Cancel is clicked", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "Draft");
    await user.click(screen.getByRole("button", { name: /^Cancel$/i }));
    expect(createHook.mutateAsync).not.toHaveBeenCalled();
  });

  it("shows create error when mutation failed", async () => {
    createHook.isError = true;
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    expect(screen.getByRole("alert")).toHaveTextContent(/Could not create project/i);
  });

  it("opens without a trigger when controlled via open + onOpenChange", () => {
    const qc = createTestQueryClient();
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider>
          <NewProjectDialog open onOpenChange={() => {}} />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /^New project$/i })).toBeInTheDocument();
  });
});
