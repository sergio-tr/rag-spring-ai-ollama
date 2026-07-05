import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import AdminHomePage from "./page";
import type { LlmCatalogModelDto, LlmCatalogResponse } from "@/types/api";
import { useMeSelectableLlmModels } from "@/features/chat/hooks/use-me-selectable-llm-models";

const apiFetch = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiFetch: (...args: unknown[]) => apiFetch(...args),
  apiProductPath: (path: string) => `/api/v5${path}`,
}));

vi.mock("@/lib/async-task", () => ({
  pollLabJob: vi.fn(),
}));

vi.mock("@/features/chat/hooks/use-me-selectable-llm-models", () => ({
  useMeSelectableLlmModels: vi.fn(() => ({
    data: { effectiveProvider: "OPENAI_COMPATIBLE", models: [] },
    isLoading: false,
    isError: false,
  })),
}));

const useMeSelectableLlmModelsMock = vi.mocked(useMeSelectableLlmModels);

const LEGACY_MODEL_IDS = ["gemma3:4b", "mistral:7b", "llama3.1:8b"] as const;

const CATALOG_PATH = "/api/v5/llm/catalog?includeRuntimeStatus=true&provider=OPENAI_COMPATIBLE";

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
  governanceAllowed: true,
};

const chatUnavailable: LlmCatalogModelDto = {
  provider: "OPENAI_COMPATIBLE",
  modelName: "deepseek-v2:16b",
  capability: "CHAT",
  available: false,
  selectableByUser: false,
  usableAsDefault: false,
  runtimeStatus: "UNAVAILABLE",
  runtimeDetail: "Model not available at runtime",
  embeddingDimensions: null,
  compatibleWithCurrentVectorStore: null,
  source: "LITELLM_CONFIGURED",
  governanceAllowed: false,
};

const embeddingIncompatible: LlmCatalogModelDto = {
  provider: "OPENAI_COMPATIBLE",
  modelName: "wrong-dim-embed:latest",
  capability: "EMBEDDING",
  available: true,
  selectableByUser: false,
  usableAsDefault: false,
  runtimeStatus: "NOT_PROBED",
  runtimeDetail: null,
  embeddingDimensions: 512,
  compatibleWithCurrentVectorStore: false,
  source: "LITELLM_CONFIGURED",
};

const catalogResponse: LlmCatalogResponse = {
  models: [chatAvailable, chatUnavailable, embeddingIncompatible],
};

describe("AdminHomePage catalog", () => {
  const qc = createTestQueryClient();

  beforeEach(() => {
    apiFetch.mockReset();
    useMeSelectableLlmModelsMock.mockReturnValue({
      data: { effectiveProvider: "OPENAI_COMPATIBLE", models: [] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useMeSelectableLlmModels>);
    apiFetch.mockImplementation(async (path: string) => {
      if (path === CATALOG_PATH) {
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
    expect(screen.queryByTestId("admin-catalog-display-name-gpt-oss:20b")).not.toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith(CATALOG_PATH);
  });

  it("shows blocked governance chip only for blocked chat models", async () => {
    renderPage();
    expect(await screen.findByTestId("admin-catalog-governance-blocked-deepseek-v2:16b")).toHaveTextContent(
      /Blocked/i,
    );
    expect(screen.queryByTestId("admin-catalog-governance-blocked-gpt-oss:20b")).not.toBeInTheDocument();
    expect(screen.queryByTestId("admin-catalog-governance-gpt-oss:20b")).not.toBeInTheDocument();
  });

  it("provider and capability are shown without redundant remote fields", async () => {
    renderPage();
    expect(await screen.findByTestId("admin-catalog-provider-gpt-oss:20b")).toHaveTextContent("Configured API catalog");
    expect(screen.getByTestId("admin-catalog-capability-gpt-oss:20b")).toHaveTextContent("CHAT");
    expect(screen.queryByTestId("admin-catalog-source-gpt-oss:20b")).not.toBeInTheDocument();
    expect(screen.getByTestId("admin-catalog-runtime-status-gpt-oss:20b")).toHaveTextContent("Available");
  });

  it("hides local pull card when effective provider is OpenAI-compatible", async () => {
    renderPage();
    await screen.findByTestId("admin-catalog-row-OPENAI_COMPATIBLE-CHAT-gpt-oss:20b");
    expect(screen.queryByTestId("admin-pull-card")).not.toBeInTheDocument();
    expect(screen.queryByText("Download local model")).not.toBeInTheDocument();
  });

  it("shows local pull card when effective provider is Ollama-native", async () => {
    useMeSelectableLlmModelsMock.mockReturnValue({
      data: { effectiveProvider: "OLLAMA_NATIVE", models: [] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useMeSelectableLlmModels>);
    renderPage();
    await screen.findByTestId("admin-catalog-row-OPENAI_COMPATIBLE-CHAT-gpt-oss:20b");
    expect(screen.getByTestId("admin-pull-card")).toBeInTheDocument();
    expect(screen.getByText("Download local model")).toBeInTheDocument();
  });

  it("unavailable model visible with warning", async () => {
    renderPage();
    expect(await screen.findByTestId("admin-catalog-row-OPENAI_COMPATIBLE-CHAT-deepseek-v2:16b")).toBeInTheDocument();
    expect(screen.getByTestId("admin-catalog-unavailable-deepseek-v2:16b")).toHaveTextContent(
      /Configured in API catalog but reported unavailable/i,
    );
    expect(screen.getByTestId("admin-catalog-unavailable-deepseek-v2:16b")).toHaveTextContent(
      /Model not available at runtime/i,
    );
  });

  it("incompatible embedding marked incompatible", async () => {
    renderPage();
    const row = await screen.findByTestId("admin-catalog-row-OPENAI_COMPATIBLE-EMBEDDING-wrong-dim-embed:latest");
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
