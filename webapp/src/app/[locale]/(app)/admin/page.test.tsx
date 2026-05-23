import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import AdminHomePage from "./page";
import type { AdminModelEntryDto } from "@/types/api";

const apiFetch = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiFetch: (...args: unknown[]) => apiFetch(...args),
  apiProductPath: (path: string) => `/api/v5${path}`,
}));

vi.mock("@/lib/async-task", () => ({
  pollLabJob: vi.fn(),
}));

const llmRow: AdminModelEntryDto = {
  id: "11111111-1111-1111-1111-111111111111",
  modelId: "llama3:latest",
  displayName: null,
  modelType: "LLM",
  enabled: true,
  available: true,
  lastCheckedAt: null,
  lastPullStatus: "OK",
  lastPullError: null,
  installedAt: null,
  tags: [],
};

const embRow: AdminModelEntryDto = {
  id: "22222222-2222-2222-2222-222222222222",
  modelId: "bge-m3:latest",
  displayName: "BGE-M3",
  modelType: "EMBEDDING",
  enabled: false,
  available: false,
  lastCheckedAt: null,
  lastPullStatus: "PROBE_FAILED",
  lastPullError: "HTTP 404",
  installedAt: null,
  tags: [],
};

describe("AdminHomePage", () => {
  const qc = createTestQueryClient();

  beforeEach(() => {
    apiFetch.mockReset();
    apiFetch.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === "/api/v5/admin/models" && (!init || init.method === undefined)) {
        return [llmRow, embRow];
      }
      if (path.startsWith("/api/v5/admin/models/") && init?.method === "DELETE") {
        return {
          id: embRow.id,
          modelId: embRow.modelId,
          modelType: "EMBEDDING",
          outcome: "DELETED",
          message: "ok",
        };
      }
      if (path === "/api/v5/admin/models/check" && init?.method === "POST") {
        return {
          modelId: "bge-m3:latest",
          requestedType: "EMBEDDING",
          existsLocal: true,
          canPull: true,
          pulled: false,
          embeddingProbeOk: false,
          matchedLocalIds: ["bge-m3:latest"],
          checkedAt: new Date().toISOString(),
          errorCode: "MODEL_EMBEDDING_PROBE_FAILED",
          errorMessage: "Embedding endpoint rejected the model",
          technicalDetail: "HTTP 404",
          pullSummary: null,
        };
      }
      throw new Error(`Unexpected apiFetch ${path}`);
    });
  });

  function renderPage() {
    return render(
      <QueryClientProvider client={qc}>
        <IntlTestProvider locale="en">
          <AdminHomePage />
        </IntlTestProvider>
      </QueryClientProvider>,
    );
  }

  it("renders LLM and embedding sections separately", async () => {
    renderPage();
    expect(await screen.findByText("LLM models")).toBeInTheDocument();
    expect(screen.getByText("Embedding models")).toBeInTheDocument();
    expect(await screen.findByTestId("admin-model-row-LLM-llama3:latest")).toBeInTheDocument();
    expect(await screen.findByTestId("admin-model-row-EMBEDDING-bge-m3:latest")).toBeInTheDocument();
  });

  it("shows friendly embedding probe failure on check", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByTestId("admin-model-row-EMBEDDING-bge-m3:latest");
    await user.type(screen.getByLabelText("Model name", { selector: "#aname" }), "bge-m3");
    await user.selectOptions(screen.getByLabelText("Type"), "EMBEDDING");
    await user.click(screen.getByRole("button", { name: /^check$/i }));
    expect(await screen.findByText(/did not pass the embedding check/i)).toBeInTheDocument();
    expect(screen.queryByText(/POST/i)).not.toBeInTheDocument();
  });

  it("wires delete action to admin models endpoint", async () => {
    const user = userEvent.setup();
    renderPage();
    const row = await screen.findByTestId("admin-model-row-EMBEDDING-bge-m3:latest");
    await user.click(within(row).getByRole("button", { name: /^delete$/i }));
    expect(apiFetch).toHaveBeenCalledWith(
      `/api/v5/admin/models/${embRow.id}`,
      expect.objectContaining({ method: "DELETE" }),
    );
    expect(await screen.findByText(/entry removed/i)).toBeInTheDocument();
  });
});
