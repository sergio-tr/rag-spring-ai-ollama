import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
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

const apiFetch = vi.mocked(apiClient.apiFetch);

describe("ProductModelRegistryCard", () => {
  const qc = createTestQueryClient();

  function Wrapper({ children }: { children: ReactNode }) {
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
    apiFetch.mockResolvedValue({
      ollamaReachable: true,
      ollamaErrorMessage: null,
      llmModels: [
        {
          modelId: "mistral:7b",
          modelType: "LLM",
          status: "MISSING",
          detail: "Model not installed locally in Ollama",
          embeddingCompatible: null,
        },
      ],
      embeddingModels: [
        {
          modelId: "nomic-embed-text",
          modelType: "EMBEDDING",
          status: "AVAILABLE",
          detail: null,
          embeddingCompatible: null,
        },
      ],
    });

    render(<ProductModelRegistryCard />, { wrapper: Wrapper });

    await waitFor(() => expect(screen.getByText("mistral:7b")).toBeInTheDocument());
    expect(screen.getByText("nomic-embed-text")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /LLM models/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /^Embedding models$/i })).toBeInTheDocument();
  });
});
