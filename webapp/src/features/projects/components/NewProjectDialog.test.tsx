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
}));

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useCreateProject: () => ({
    mutateAsync: createHook.mutateAsync,
    reset: createHook.reset,
    isPending: createHook.isPending,
  }),
}));

const notifyCreatedMock = vi.hoisted(() => vi.fn());

vi.mock("@/features/projects/hooks/use-project-create-feedback-notifier", () => ({
  useProjectCreateFeedbackNotifier: () => notifyCreatedMock,
}));

vi.mock("@/features/chat/hooks/use-me-selectable-llm-models", () => ({
  useMeSelectableLlmModels: (capability: string) => ({
    data: {
      effectiveProvider: "OPENAI_COMPATIBLE",
      models:
        capability === "CHAT"
          ? [{ modelName: "hf-chat:latest", displayName: "hf-chat:latest", selectable: true }]
          : [{ modelName: "hf-embed:latest", displayName: "hf-embed:latest", selectable: true }],
    },
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
    apiMock.apiFetch.mockClear();
    notifyCreatedMock.mockClear();
    createHook.isPending = false;
  });

  it("shows fixed indexing guidance and Assistant Configuration link", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    expect(screen.getByTestId("project-create-indexing-fixed-hint")).toHaveTextContent(
      /Index settings are fixed after project creation/i,
    );
    expect(screen.getByRole("link", { name: /Assistant Configuration/i })).toHaveAttribute(
      "href",
      "/en/settings/user",
    );
    expect(screen.queryByLabelText(/chat model/i)).not.toBeInTheDocument();
  });

  it("localizes Assistant Configuration link for Spanish locale", async () => {
    const user = userEvent.setup();
    const qc = createTestQueryClient();
    render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="es">
          <NewProjectDialog />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    expect(screen.getByRole("link", { name: /Assistant Configuration/i })).toHaveAttribute(
      "href",
      "/es/settings/user",
    );
  });

  it("shows description helper and project context guidance", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    expect(screen.getByTestId("project-create-description-hint")).toHaveTextContent(
      /does not change assistant behavior/i,
    );
    expect(screen.getByText(/Project context and assistant instructions/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Short description \/ Notes/i)).toBeInTheDocument();
  });

  it("submit payload includes description and excludes llmModel", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "My project");
    await user.type(screen.getByLabelText(/Short description \/ Notes/i), "A note");
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    await vi.waitFor(() => expect(createHook.mutateAsync).toHaveBeenCalled());
    const payload = createHook.mutateAsync.mock.calls[0]?.[0] as Record<string, unknown>;
    expect(payload.description).toBe("A note");
    expect(payload).not.toHaveProperty("llmModel");
    expect(payload).not.toHaveProperty("llmModelId");
    expect(payload).not.toHaveProperty("classifierModelId");
  });

  it("does not show reindex CTA in create dialog", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    expect(screen.queryByRole("button", { name: /reindex/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/reindex/i)).not.toBeInTheDocument();
  });

  it("shows materialization strategy help for each normal strategy", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    const select = screen.getByTestId("project-create-materialization-strategy");
    const strategies = ["CHUNK_LEVEL", "DOCUMENT_LEVEL", "HYBRID"] as const;
    for (const strategy of strategies) {
      await user.selectOptions(select, strategy);
      expect(screen.getByTestId("project-create-materialization-help")).not.toBeEmptyDOMElement();
    }
  });

  it("does not show STRUCTURED_SEARCH in normal project creation", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    const select = screen.getByTestId("project-create-materialization-strategy");
    expect(
      Array.from(select.querySelectorAll("option")).map((option) => option.getAttribute("value")),
    ).toEqual(["CHUNK_LEVEL", "DOCUMENT_LEVEL", "HYBRID"]);
  });

  it("shows STRUCTURED_SEARCH only when advanced indexing flag is enabled", async () => {
    vi.stubEnv("NEXT_PUBLIC_ENABLE_STRUCTURED_SEARCH_INDEXING", "true");
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    const select = screen.getByTestId("project-create-materialization-strategy");
    expect(
      Array.from(select.querySelectorAll("option")).map((option) => option.getAttribute("value")),
    ).toContain("STRUCTURED_SEARCH");
    await user.selectOptions(select, "STRUCTURED_SEARCH");
    expect(screen.getByTestId("project-create-structured-search-advanced-warning")).toBeInTheDocument();
    vi.unstubAllEnvs();
  });

  it("shows STRUCTURED_SEARCH informational warning when metadata is enabled and advanced flag is on", async () => {
    vi.stubEnv("NEXT_PUBLIC_ENABLE_STRUCTURED_SEARCH_INDEXING", "true");
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.selectOptions(screen.getByTestId("project-create-materialization-strategy"), "STRUCTURED_SEARCH");
    await user.click(screen.getByTestId("project-create-metadata-capability"));
    expect(screen.getByTestId("project-create-index-combination-warning")).toHaveTextContent(
      /does not support vector retrieval/i,
    );
    expect(screen.queryByTestId("project-create-index-combination-blocked")).not.toBeInTheDocument();
    vi.unstubAllEnvs();
  });

  it("blocks STRUCTURED_SEARCH when metadata capability is disabled and advanced flag is on", async () => {
    vi.stubEnv("NEXT_PUBLIC_ENABLE_STRUCTURED_SEARCH_INDEXING", "true");
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.selectOptions(screen.getByTestId("project-create-materialization-strategy"), "STRUCTURED_SEARCH");
    expect(screen.getByTestId("project-create-index-combination-blocked")).toHaveTextContent(
      /requires metadata-aware indexing/i,
    );
    expect(screen.getByRole("button", { name: /^Create$/i })).toBeDisabled();
    vi.unstubAllEnvs();
  });

  it("describes metadata checkbox as index capability without extraction claims", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    expect(screen.getByTestId("project-create-metadata-capability")).toBeInTheDocument();
    expect(screen.getByTestId("project-create-metadata-capability").closest("label")).toHaveTextContent(
      /Metadata-aware index capability/i,
    );
    expect(screen.getByTestId("project-create-metadata-helper")).toHaveTextContent(
      /preset compatibility/i,
    );
    expect(screen.getByTestId("project-create-metadata-helper")).toHaveTextContent(
      /structured metadata/i,
    );
  });

  it("submit payload sends canonical materializationStrategy values", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "Canonical");
    await user.selectOptions(screen.getByTestId("project-create-materialization-strategy"), "HYBRID");
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    await vi.waitFor(() => expect(createHook.mutateAsync).toHaveBeenCalled());
    const payload = createHook.mutateAsync.mock.calls[0]?.[0] as {
      initialIndexProfile?: { materializationStrategy?: string };
    };
    expect(payload.initialIndexProfile?.materializationStrategy).toBe("HYBRID");
    expect(payload.initialIndexProfile?.materializationStrategy).not.toMatch(/^(CHUNK|DOCUMENT|STRUCT)$/);
  });


  it("does not put project llm config after create (models live in Assistant Configuration)", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "With model");
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    await vi.waitFor(() => expect(createHook.mutateAsync).toHaveBeenCalled());
    expect(apiMock.apiFetch).not.toHaveBeenCalled();
  });

  it("shows validation error when name is empty", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent(/Enter a project name/i);
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

  it("closes without error after successful create", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "Created ok");
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    await vi.waitFor(() => expect(createHook.mutateAsync).toHaveBeenCalled());
    expect(screen.queryByTestId("project-create-error")).not.toBeInTheDocument();
  });

  it("notifies shared feedback on success when no onCreated override", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "Created ok");
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    await vi.waitFor(() => expect(notifyCreatedMock).toHaveBeenCalled());
  });

  it("does not show stale mutation error when dialog opens", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    expect(screen.queryByTestId("project-create-error")).not.toBeInTheDocument();
  });

  it("shows create error when mutation failed", async () => {
    createHook.mutateAsync.mockRejectedValueOnce(new Error("fail"));
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /^New project$/i }));
    await user.type(screen.getByLabelText(/^Name$/i), "Fail");
    await user.click(screen.getByRole("button", { name: /^Create$/i }));
    await vi.waitFor(() => expect(screen.getByTestId("project-create-error")).toBeInTheDocument());
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

  describe("materialization strategy and metadata combination feedback", () => {
    async function openCreateDialog(user: ReturnType<typeof userEvent.setup>) {
      renderDialog();
      await user.click(screen.getByRole("button", { name: /^New project$/i }));
    }

    async function selectStrategy(
      user: ReturnType<typeof userEvent.setup>,
      strategy: "CHUNK_LEVEL" | "DOCUMENT_LEVEL" | "HYBRID" | "STRUCTURED_SEARCH",
    ) {
      await user.selectOptions(screen.getByTestId("project-create-materialization-strategy"), strategy);
    }

    async function setMetadataEnabled(user: ReturnType<typeof userEvent.setup>, enabled: boolean) {
      const checkbox = screen.getByTestId("project-create-metadata-capability") as HTMLInputElement;
      if (checkbox.checked !== enabled) {
        await user.click(checkbox);
      }
    }

    async function submitWithName(user: ReturnType<typeof userEvent.setup>) {
      await user.type(screen.getByLabelText(/^Name$/i), "Combo test");
      await user.click(screen.getByRole("button", { name: /^Create$/i }));
    }

    it("CHUNK_LEVEL + metadata=false allows submit without warning", async () => {
      const user = userEvent.setup();
      await openCreateDialog(user);
      await selectStrategy(user, "CHUNK_LEVEL");
      await setMetadataEnabled(user, false);
      expect(screen.queryByTestId("project-create-index-combination-warning")).not.toBeInTheDocument();
      expect(screen.queryByTestId("project-create-index-combination-blocked")).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^Create$/i })).toBeEnabled();
      await submitWithName(user);
      await vi.waitFor(() => expect(createHook.mutateAsync).toHaveBeenCalled());
    });

    it("CHUNK_LEVEL + metadata=true allows submit without warning", async () => {
      const user = userEvent.setup();
      await openCreateDialog(user);
      await selectStrategy(user, "CHUNK_LEVEL");
      await setMetadataEnabled(user, true);
      expect(screen.queryByTestId("project-create-index-combination-warning")).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^Create$/i })).toBeEnabled();
      await submitWithName(user);
      await vi.waitFor(() => expect(createHook.mutateAsync).toHaveBeenCalled());
    });

    it("DOCUMENT_LEVEL + metadata=false allows submit without warning", async () => {
      const user = userEvent.setup();
      await openCreateDialog(user);
      await selectStrategy(user, "DOCUMENT_LEVEL");
      await setMetadataEnabled(user, false);
      expect(screen.queryByTestId("project-create-index-combination-warning")).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^Create$/i })).toBeEnabled();
      await submitWithName(user);
      await vi.waitFor(() => expect(createHook.mutateAsync).toHaveBeenCalled());
    });

    it("DOCUMENT_LEVEL + metadata=true allows submit without warning", async () => {
      const user = userEvent.setup();
      await openCreateDialog(user);
      await selectStrategy(user, "DOCUMENT_LEVEL");
      await setMetadataEnabled(user, true);
      expect(screen.queryByTestId("project-create-index-combination-warning")).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^Create$/i })).toBeEnabled();
      await submitWithName(user);
      await vi.waitFor(() => expect(createHook.mutateAsync).toHaveBeenCalled());
    });

    it("HYBRID + metadata=false shows warning and allows submit", async () => {
      const user = userEvent.setup();
      await openCreateDialog(user);
      await selectStrategy(user, "HYBRID");
      await setMetadataEnabled(user, false);
      expect(screen.getByTestId("project-create-index-combination-warning")).toHaveTextContent(
        /HYBRID indexing without metadata/i,
      );
      expect(screen.getByRole("button", { name: /^Create$/i })).toBeEnabled();
      await submitWithName(user);
      await vi.waitFor(() => expect(createHook.mutateAsync).toHaveBeenCalled());
    });

    it("HYBRID + metadata=true shows no warning and allows submit", async () => {
      const user = userEvent.setup();
      await openCreateDialog(user);
      await selectStrategy(user, "HYBRID");
      await setMetadataEnabled(user, true);
      expect(screen.queryByTestId("project-create-index-combination-warning")).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^Create$/i })).toBeEnabled();
      await submitWithName(user);
      await vi.waitFor(() => expect(createHook.mutateAsync).toHaveBeenCalled());
    });

    it("STRUCTURED_SEARCH + metadata=false blocks submit", async () => {
      vi.stubEnv("NEXT_PUBLIC_ENABLE_STRUCTURED_SEARCH_INDEXING", "true");
      const user = userEvent.setup();
      await openCreateDialog(user);
      await selectStrategy(user, "STRUCTURED_SEARCH");
      await setMetadataEnabled(user, false);
      expect(screen.getByTestId("project-create-index-combination-blocked")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^Create$/i })).toBeDisabled();
      await submitWithName(user);
      expect(createHook.mutateAsync).not.toHaveBeenCalled();
      vi.unstubAllEnvs();
    });

    it("STRUCTURED_SEARCH + metadata=true allows submit", async () => {
      vi.stubEnv("NEXT_PUBLIC_ENABLE_STRUCTURED_SEARCH_INDEXING", "true");
      const user = userEvent.setup();
      await openCreateDialog(user);
      await selectStrategy(user, "STRUCTURED_SEARCH");
      await setMetadataEnabled(user, true);
      expect(screen.queryByTestId("project-create-index-combination-blocked")).not.toBeInTheDocument();
      expect(screen.getByTestId("project-create-index-combination-warning")).toHaveTextContent(
        /does not support vector retrieval/i,
      );
      expect(screen.getByRole("button", { name: /^Create$/i })).toBeEnabled();
      await submitWithName(user);
      await vi.waitFor(() => expect(createHook.mutateAsync).toHaveBeenCalled());
      vi.unstubAllEnvs();
    });
  });
});
