import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";
import { AdminLlmCatalogSection } from "./AdminLlmCatalogSection";
import type { LlmCatalogModelDto } from "@/types/api";

const LEGACY_MODEL_IDS = ["gemma3:4b", "mistral:7b", "llama3.1:8b"] as const;

const t = {
  catalogEmptySection: "No models configured in this section.",
  catalogRuntimeUnavailable: "Configured but unavailable at runtime",
  catalogRuntimeUnavailableOpenAI: "Configured in API catalog but reported unavailable",
  catalogRuntimeNotProbedNote: "Configured in catalog; remote provider runtime was not probed.",
  catalogRuntimeStatusConfigured: "Configured",
  catalogRuntimeStatusNotProbed: "Not probed",
  catalogRuntimeStatusNotProbedOpenAI: "Configured (remote not probed)",
  catalogRuntimeStatusAvailable: "Available",
  catalogRuntimeStatusUnavailable: "Unavailable",
  catalogRuntimeStatusUnavailableOpenAI: "Unavailable on API",
  catalogRuntimeStatusProbeFailed: "Probe failed",
  catalogSourceLitellmConfigured: "Configured API catalog",
  catalogSourceConfiguredCatalog: "Configured catalog",
  catalogSourceOllamaLive: "Local model server catalog",
  catalogSourceUnknown: "Unknown",
  catalogSourceProperties: "Properties file",
  catalogVectorIncompatible: "Incompatible with vector store",
  catalogIndexingDisabledTooltip:
    "Cannot be used for indexing or evaluation until compatible with the vector store and available at runtime.",
  catalogPull: "Pull",
  catalogProvider: "Provider",
  catalogProviderRemoteApi: "Configured API catalog",
  catalogProviderLocalServer: "Local model server",
  catalogCapability: "Capability",
  catalogModelName: "Model name",
  catalogDisplayName: "Display name",
  catalogConfigured: "Configured",
  catalogConfiguredYes: "Yes",
  catalogConfiguredNo: "No",
  catalogSource: "Source",
  catalogRuntimeStatus: "Runtime status",
  catalogEmbeddingDimensions: "Embedding dimensions",
  catalogVectorCompatible: "Vector store compatible",
  catalogVectorCompatibleYes: "Yes",
  catalogVectorCompatibleNo: "No",
  catalogVectorCompatibleNa: "N/A",
  modelAvailable: "Available",
  modelMissing: "Not installed",
};

const chatOpenAiNotProbed: LlmCatalogModelDto = {
  provider: "OPENAI_COMPATIBLE",
  modelName: "gpt-oss:20b",
  displayName: "GPT OSS 20B",
  configured: true,
  capability: "CHAT",
  available: true,
  selectableByUser: true,
  usableAsDefault: true,
  runtimeStatus: "NOT_PROBED",
  runtimeDetail: "Configured in catalog; remote provider runtime not probed",
  embeddingDimensions: null,
  compatibleWithCurrentVectorStore: null,
  source: "LITELLM_CONFIGURED",
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

function renderSection(rows: LlmCatalogModelDto[]) {
  return render(
    <IntlTestProvider locale="en">
      <AdminLlmCatalogSection title="Catalog" rows={rows} emptyLabel={t.catalogEmptySection} t={t} busyKey={null} />
    </IntlTestProvider>,
  );
}

describe("AdminLlmCatalogSection", () => {
  it("admin shows configured catalog models", () => {
    renderSection([chatOpenAiNotProbed, chatUnavailable, embeddingIncompatible]);

    expect(screen.getByTestId("admin-catalog-row-OPENAI_COMPATIBLE-CHAT-gpt-oss:20b")).toBeInTheDocument();
    expect(screen.getByTestId("admin-catalog-display-name-gpt-oss:20b")).toHaveTextContent("GPT OSS 20B");
    expect(screen.getByTestId("admin-catalog-configured-gpt-oss:20b")).toHaveTextContent("Yes");
    expect(screen.getByTestId("admin-catalog-provider-gpt-oss:20b")).toHaveTextContent("Configured API catalog");
    expect(screen.getByTestId("admin-catalog-capability-gpt-oss:20b")).toHaveTextContent("CHAT");
    expect(screen.getByTestId("admin-catalog-runtime-status-gpt-oss:20b")).toHaveTextContent(
      /Configured \(remote not probed\)/i,
    );
    expect(screen.getByTestId("admin-catalog-source-gpt-oss:20b")).toHaveTextContent(/Configured API catalog/i);
    expect(screen.getByTestId("admin-catalog-not-probed-gpt-oss:20b")).toBeInTheDocument();
    expect(screen.queryByTestId("admin-catalog-unavailable-gpt-oss:20b")).not.toBeInTheDocument();
  });

  it("unavailable model visible with warning", () => {
    renderSection([chatUnavailable]);

    expect(screen.getByTestId("admin-catalog-row-OLLAMA_NATIVE-CHAT-ollama-missing:latest")).toBeInTheDocument();
    expect(screen.getByTestId("admin-catalog-unavailable-ollama-missing:latest")).toHaveTextContent(
      /Configured but unavailable at runtime/i,
    );
    expect(screen.getByTestId("admin-catalog-unavailable-ollama-missing:latest")).toHaveTextContent(
      /Model not installed locally/i,
    );
  });

  it("incompatible embedding marked incompatible", () => {
    renderSection([embeddingIncompatible]);

    const row = screen.getByTestId("admin-catalog-row-OLLAMA_NATIVE-EMBEDDING-wrong-dim-embed:latest");
    expect(row).toHaveAttribute("data-indexing-disabled", "true");
    expect(row).toHaveAttribute("title", t.catalogIndexingDisabledTooltip);
    expect(screen.getByTestId("admin-catalog-incompatible-wrong-dim-embed:latest")).toHaveTextContent(
      /Incompatible with vector store/i,
    );
    expect(screen.getByTestId("admin-catalog-embedding-dims-wrong-dim-embed:latest")).toHaveTextContent("512");
    expect(screen.getByTestId("admin-catalog-vector-compatible-wrong-dim-embed:latest")).toHaveTextContent("No");
  });

  it("no hardcoded model list", () => {
    renderSection([chatOpenAiNotProbed]);
    const text = document.body.textContent ?? "";
    for (const legacy of LEGACY_MODEL_IDS) {
      expect(text).not.toContain(legacy);
    }
  });
});
