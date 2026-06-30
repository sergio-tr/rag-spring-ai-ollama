import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import AdminHomePage from "./page";
import type { LlmCatalogModelDto, LlmCatalogResponse } from "@/types/api";

const apiFetch = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiFetch: (...args: unknown[]) => apiFetch(...args),
  apiProductPath: (path: string) => `/api/v5${path}`,
}));

vi.mock("@/lib/async-task", () => ({
  pollLabJob: vi.fn(),
}));

const LEGACY_MODEL_IDS = ["gemma3:4b", "mistral:7b", "llama3.1:8b"] as const;

const chatAvailable: LlmCatalogModelDto = {
  provider: "OPENAI_COMPATIBLE",
  modelName: "gpt-oss:20b",
  displayName: "GPT OSS 20B",
  configured: true,
  capability: "CHAT",
  available: true,
  selectableByUser: true,
  usableAsDefault: true,
  runtimeStatus: "AVAILABLE",
  runtimeDetail: null,
  embeddingDimensions: null,
  compatibleWithCurrentVectorStore: null,
  source: "PROPERTIES",
};

const chatUnavailable: LlmCatalogModelDto = {
  provider: "OLLAMA_NATIVE",
  modelName: "ollama-missing:latest",
  capability: "CHAT",
  available: false,
  selectableByUser: false,
  usableAsDefault: false,
  runtimeStatus: "UNAVAILABLE",
  runtimeDetail: "Model not installed locally",
  embeddingDimensions: null,
  compatibleWithCurrentVectorStore: null,
  source: "PROPERTIES",
};

const embeddingIncompatible: LlmCatalogModelDto = {
  provider: "OLLAMA_NATIVE",
  modelName: "wrong-dim-embed:latest",
  capability: "EMBEDDING",
  available: true,
  selectableByUser: false,
  usableAsDefault: false,
  runtimeStatus: "AVAILABLE",
  runtimeDetail: null,
  embeddingDimensions: 512,
  compatibleWithCurrentVectorStore: false,
  source: "PROPERTIES",
};

const catalogResponse: LlmCatalogResponse = {
  models: [chatAvailable, chatUnavailable, embeddingIncompatible],
};

describe("AdminHomePage catalog", () => {
  const qc = createTestQueryClient();

  beforeEach(() => {
    apiFetch.mockReset();
    apiFetch.mockImplementation(async (path: string) => {
      if (path === "/api/v5/llm/catalog?includeRuntimeStatus=true") {
        return catalogResponse;
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

  it("admin shows configured catalog models", async () => {
    renderPage();
    expect(await screen.findByText("Configured model catalog")).toBeInTheDocument();
    expect(await screen.findByTestId("admin-catalog-row-OPENAI_COMPATIBLE-CHAT-gpt-oss:20b")).toBeInTheDocument();
    expect(await screen.findByTestId("admin-catalog-display-name-gpt-oss:20b")).toHaveTextContent("GPT OSS 20B");
    expect(apiFetch).toHaveBeenCalledWith("/api/v5/llm/catalog?includeRuntimeStatus=true");
  });

  it("provider and capability are shown", async () => {
    renderPage();
    expect(await screen.findByTestId("admin-catalog-provider-gpt-oss:20b")).toHaveTextContent("Configured API catalog");
    expect(screen.getByTestId("admin-catalog-capability-gpt-oss:20b")).toHaveTextContent("CHAT");
    expect(screen.getByTestId("admin-catalog-source-gpt-oss:20b")).toHaveTextContent("Properties file");
    expect(screen.getByTestId("admin-catalog-runtime-status-gpt-oss:20b")).toHaveTextContent("Available");
  });

  it("unavailable model visible with warning", async () => {
    renderPage();
    expect(await screen.findByTestId("admin-catalog-row-OLLAMA_NATIVE-CHAT-ollama-missing:latest")).toBeInTheDocument();
    expect(screen.getByTestId("admin-catalog-unavailable-ollama-missing:latest")).toHaveTextContent(
      /Configured but unavailable at runtime/i,
    );
    expect(screen.getByTestId("admin-catalog-unavailable-ollama-missing:latest")).toHaveTextContent(
      /Model not installed locally/i,
    );
  });

  it("incompatible embedding marked incompatible", async () => {
    renderPage();
    const row = await screen.findByTestId("admin-catalog-row-OLLAMA_NATIVE-EMBEDDING-wrong-dim-embed:latest");
    expect(row).toHaveAttribute("data-indexing-disabled", "true");
    expect(screen.getByTestId("admin-catalog-incompatible-wrong-dim-embed:latest")).toHaveTextContent(
      /Incompatible with vector store/i,
    );
    expect(screen.getByTestId("admin-catalog-embedding-dims-wrong-dim-embed:latest")).toHaveTextContent("512");
    expect(screen.getByTestId("admin-catalog-vector-compatible-wrong-dim-embed:latest")).toHaveTextContent("No");
  });

  it("no hardcoded model list", async () => {
    renderPage();
    await screen.findByTestId("admin-catalog-row-OPENAI_COMPATIBLE-CHAT-gpt-oss:20b");
    const card = screen.getByTestId("admin-models-card");
    const text = card.textContent ?? "";
    for (const legacy of LEGACY_MODEL_IDS) {
      expect(text).not.toContain(legacy);
    }
    expect(apiFetch).not.toHaveBeenCalledWith("/api/v5/admin/models", expect.anything());
  });
});
