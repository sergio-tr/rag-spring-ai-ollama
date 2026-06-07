import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { ProductModelRegistryCard } from "./ProductModelRegistryCard";
import { createTestQueryClient } from "@/test-utils/query-client";
import { IntlTestProvider } from "@/test-utils/intl";

vi.mock("@/lib/api-client", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

vi.mock("@/lib/async-task", () => ({
  pollLabJob: vi.fn(async () => ({
    id: "pull-job",
    taskType: "MODEL_PULL",
    status: "SUCCEEDED",
    terminal: true,
    progressText: null,
    errorMessage: null,
    failureCode: null,
    result: null,
  })),
}));

const apiFetch = vi.mocked(apiClient.apiFetch);

const missingLlm = {
  modelId: "mistral:7b",
  modelType: "LLM",
  status: "MISSING",
  detail: "Model not installed locally in Ollama",
  embeddingCompatible: null,
} as const;

const availableEmbedding = {
  modelId: "nomic-embed-text",
  modelType: "EMBEDDING",
  status: "AVAILABLE",
  detail: null,
  embeddingCompatible: null,
} as const;

function registryResponse(overrides: Partial<Awaited<ReturnType<typeof apiFetch>>> = {}) {
  return {
    ollamaReachable: true,
    ollamaErrorMessage: null,
    llmModels: [missingLlm],
    embeddingModels: [availableEmbedding],
    ...overrides,
  };
}

describe("ProductModelRegistryCard", () => {
  const qc = createTestQueryClient();

  function Wrapper({ children }: Readonly<{ children: ReactNode }>) {
    return (
      <IntlTestProvider>
        <QueryClientProvider client={qc}>{children}</QueryClientProvider>
      </IntlTestProvider>
    );
  }

  beforeEach(() => {
    apiFetch.mockReset();
    qc.clear();
  });

  it("renders LLM and embedding sections when registry loads", async () => {
    apiFetch.mockResolvedValue(registryResponse());

    render(<ProductModelRegistryCard />, { wrapper: Wrapper });

    await waitFor(() => expect(screen.getByText("mistral:7b")).toBeInTheDocument());
    expect(screen.getByText("nomic-embed-text")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /LLM models/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /^Embedding models$/i })).toBeInTheDocument();
  });

  it("shows ollama outage and embedding compatibility errors", async () => {
    apiFetch.mockResolvedValue(
      registryResponse({
        ollamaReachable: false,
        ollamaErrorMessage: "connection refused",
        embeddingModels: [
          {
            modelId: "bad-embed",
            modelType: "EMBEDDING",
            status: "ERROR",
            detail: "dimension mismatch",
            embeddingCompatible: false,
          },
        ],
      }),
    );

    render(<ProductModelRegistryCard />, { wrapper: Wrapper });

    expect(await screen.findByText(/Ollama is unreachable/i)).toBeInTheDocument();
    expect(screen.getByText(/connection refused/i)).toBeInTheDocument();
    expect(screen.getByText("bad-embed")).toBeInTheDocument();
    expect(screen.getAllByText(/dimension mismatch/i).length).toBeGreaterThan(0);
  });

  it("verifies a model through the user-safe check endpoint", async () => {
    const user = userEvent.setup();
    apiFetch.mockResolvedValue(registryResponse());

    render(<ProductModelRegistryCard />, { wrapper: Wrapper });

    await screen.findByText("mistral:7b");
    await user.click(screen.getAllByRole("button", { name: /Verify/i })[0]);

    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith(
        expect.stringContaining("/model-registry/check"),
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ modelId: "mistral:7b", probeEmbedding: true }),
        }),
      ),
    );
  });

  it("queues pull for missing models and disables pull for available models", async () => {
    const user = userEvent.setup();
    apiFetch.mockImplementation(async (path) => {
      const url = String(path);
      if (url.includes("/model-registry/pull")) {
        return {
          jobId: "pull-job",
          status: "RUNNING",
          pollPath: "/lab/jobs/pull-job",
          streamPath: "/lab/jobs/pull-job/events",
        };
      }
      return registryResponse();
    });

    render(<ProductModelRegistryCard />, { wrapper: Wrapper });

    await screen.findByText("mistral:7b");
    const pullButtons = screen.getAllByRole("button", { name: /Pull/i });
    expect(pullButtons[0]).toBeEnabled();
    expect(pullButtons[1]).toBeDisabled();

    await user.click(pullButtons[0]);

    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith(
        expect.stringContaining("/model-registry/pull"),
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ modelId: "mistral:7b" }),
        }),
      ),
    );
  });
});
