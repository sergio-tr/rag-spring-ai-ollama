import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import { NewProjectDialog } from "./NewProjectDialog";
import { ProjectCreateError } from "@/features/projects/lib/project-create-errors";

const createHook = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
  reset: vi.fn(),
  isPending: false,
  isError: false,
  isSuccess: false,
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useCreateProject: () => ({
    mutateAsync: createHook.mutateAsync,
    reset: createHook.reset,
    isPending: createHook.isPending,
    isError: createHook.isError,
    isSuccess: createHook.isSuccess,
  }),
}));

vi.mock("@/features/chat/hooks/use-me-selectable-llm-models", () => ({
  useMeSelectableLlmModels: () => ({
    data: { effectiveProvider: "OPENAI_COMPATIBLE", models: [] },
    isLoading: false,
    isError: false,
  }),
}));

const apiMock = vi.hoisted(() => ({
  apiFetch: vi.fn().mockResolvedValue({}),
  apiProductPath: (p: string) => p,
}));

vi.mock("@/lib/api-client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api-client")>("@/lib/api-client");
  return { ...actual, apiFetch: apiMock.apiFetch, apiProductPath: apiMock.apiProductPath };
});

function renderDialog(onCreated = vi.fn()) {
  const qc = createTestQueryClient();
  return {
    onCreated,
    ...render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider>
          <NewProjectDialog onCreated={onCreated} />
        </IntlTestProvider>
      </QueryClientProvider>,
    ),
  };
}

describe("CreateProjectFlow", () => {
  beforeEach(() => {
    createHook.mutateAsync.mockReset();
    createHook.isPending = false;
    createHook.isError = false;
    createHook.isSuccess = false;
    apiMock.apiFetch.mockClear();
  });

  it("disables submit while create is pending", async () => {
    createHook.isPending = true;
    renderDialog();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    expect(screen.getByRole("button", { name: /^Create$/i })).toBeDisabled();
  });

  it("calls onCreated and closes without create error on success", async () => {
    const onCreated = vi.fn();
    createHook.mutateAsync.mockResolvedValue({
      project: { id: "p1", name: "Flow ok", docCount: 0, convCount: 0, updatedAt: "" },
    });
    const user = userEvent.setup();
    renderDialog(onCreated);
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "Flow ok");
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    await vi.waitFor(() => expect(onCreated).toHaveBeenCalled());
    expect(screen.queryByTestId("project-create-error")).not.toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("shows create error for CREATE_FAILED", async () => {
    createHook.mutateAsync.mockRejectedValue(new ProjectCreateError("CREATE_FAILED"));
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "Fail");
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    await vi.waitFor(() => expect(screen.getByTestId("project-create-error")).toBeInTheDocument());
    expect(screen.getByTestId("project-create-error")).toHaveTextContent(/Could not create project/i);
  });

  it("shows incomplete-response error without claiming create failed when response incomplete", async () => {
    createHook.mutateAsync.mockRejectedValue(
      new ProjectCreateError("PROJECT_CREATED_RESPONSE_INCOMPLETE"),
    );
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "Maybe");
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    await vi.waitFor(() => expect(screen.getByTestId("project-create-error")).toBeInTheDocument());
    expect(screen.getByTestId("project-create-error")).toHaveTextContent(/incomplete/i);
  });

  it("passes activateFailed to onCreated without showing create error", async () => {
    const onCreated = vi.fn();
    createHook.mutateAsync.mockResolvedValue({
      project: { id: "p2", name: "Partial", docCount: 0, convCount: 0, updatedAt: "" },
      activateFailed: true,
    });
    const user = userEvent.setup();
    renderDialog(onCreated);
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "Partial");
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    await vi.waitFor(() =>
      expect(onCreated).toHaveBeenCalledWith(expect.objectContaining({ activateFailed: true })),
    );
    expect(screen.queryByTestId("project-create-error")).not.toBeInTheDocument();
  });
});
