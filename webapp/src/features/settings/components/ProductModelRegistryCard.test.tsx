import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { ProductModelRegistryCard } from "./ProductModelRegistryCard";
import { createTestQueryClient } from "@/test-utils/query-client";
import { IntlTestProvider } from "@/test-utils/intl";
import { useMeSelectableLlmModels } from "@/features/chat/hooks/use-me-selectable-llm-models";

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

vi.mock("@/features/chat/hooks/use-me-selectable-llm-models", () => ({
  useMeSelectableLlmModels: vi.fn(() => ({
    data: { effectiveProvider: "OLLAMA_NATIVE", models: [] },
    isLoading: false,
    isError: false,
  })),
}));

const apiFetch = vi.mocked(apiClient.apiFetch);
const useMeSelectableLlmModelsMock = vi.mocked(useMeSelectableLlmModels);

const availableLlm = {
  modelId: "qwen:latest",
  modelType: "LLM",
  status: "AVAILABLE",
  detail: null,
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
    llmModels: [availableLlm],
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
    useMeSelectableLlmModelsMock.mockReturnValue({
      data: { effectiveProvider: "OLLAMA_NATIVE", models: [] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useMeSelectableLlmModels>);
  });

  it("renders only available configured models in recommended sections", async () => {
    apiFetch.mockResolvedValue(
      registryResponse({
        llmModels: [
          availableLlm,
          {
            modelId: "mistral:7b",
            modelType: "LLM",
            status: "MISSING",
            detail: "Model not installed locally in Ollama",
            embeddingCompatible: null,
          },
        ],
      }),
    );

    render(<ProductModelRegistryCard />, { wrapper: Wrapper });

    await waitFor(() => expect(screen.getByText("qwen:latest")).toBeInTheDocument());
    expect(screen.getByText("nomic-embed-text")).toBeInTheDocument();
    expect(screen.queryByText("mistral:7b")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /Chat models/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /^Embedding models$/i })).toBeInTheDocument();
  });

  it("shows neutral server unreachable when Ollama provider is active but registry is down", async () => {
    apiFetch.mockResolvedValue(
      registryResponse({
        ollamaReachable: false,
        ollamaErrorMessage: "connection refused",
      }),
    );

    render(<ProductModelRegistryCard />, { wrapper: Wrapper });

    expect(await screen.findByTestId("model-registry-server-unreachable")).toHaveTextContent(
      /model server is not reachable/i,
    );
    expect(screen.queryByText(/Ollama is unreachable/i)).not.toBeInTheDocument();
    expect(screen.getByText(/connection refused/i)).toBeInTheDocument();
  });

  it("hides Ollama-specific unreachable banner when effective provider is OpenAI-compatible", async () => {
    useMeSelectableLlmModelsMock.mockReturnValue({
      data: { effectiveProvider: "OPENAI_COMPATIBLE", models: [] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useMeSelectableLlmModels>);
    apiFetch.mockResolvedValue(
      registryResponse({
        ollamaReachable: false,
        ollamaErrorMessage: "connection refused",
      }),
    );

    render(<ProductModelRegistryCard />, { wrapper: Wrapper });

    await screen.findByText("qwen:latest");
    expect(screen.queryByTestId("model-registry-server-unreachable")).not.toBeInTheDocument();
    expect(screen.queryByText(/Ollama is unreachable/i)).not.toBeInTheDocument();
  });

  it("verifies a model through the user-safe check endpoint", async () => {
    const user = userEvent.setup();
    apiFetch.mockResolvedValue(registryResponse());

    render(<ProductModelRegistryCard />, { wrapper: Wrapper });

    await screen.findByText("qwen:latest");
    await user.click(screen.getAllByRole("button", { name: /Verify/i })[0]);

    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith(
        expect.stringContaining("/model-registry/check"),
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ modelId: "qwen:latest", probeEmbedding: true }),
        }),
      ),
    );
  });

  it("disables pull for available recommended models", async () => {
    apiFetch.mockResolvedValue(registryResponse());

    render(<ProductModelRegistryCard />, { wrapper: Wrapper });

    await screen.findByText("qwen:latest");
    const pullButtons = screen.getAllByRole("button", { name: /Pull/i });
    expect(pullButtons.every((button) => button.hasAttribute("disabled"))).toBe(true);
  });
});
