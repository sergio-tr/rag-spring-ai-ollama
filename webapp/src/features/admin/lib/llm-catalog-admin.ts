import type { LlmCatalogModelDto, LlmCatalogRuntimeStatus, LlmCatalogSource, LlmProvider } from "@/types/api";

export function catalogRowKey(model: LlmCatalogModelDto): string {
  return `${model.provider}:${model.capability}:${model.modelName}`;
}

export function catalogDisplayName(model: LlmCatalogModelDto): string {
  const display = model.displayName?.trim();
  return display || model.modelName;
}

const CONFIGURED_SOURCES = new Set<LlmCatalogSource>([
  "PROPERTIES",
  "CONFIGURED_CATALOG",
  "LITELLM_CONFIGURED",
  "OLLAMA_LIVE",
]);

export function isCatalogConfigured(model: LlmCatalogModelDto): boolean {
  if (typeof model.configured === "boolean") {
    return model.configured;
  }
  return CONFIGURED_SOURCES.has(model.source);
}

export function isCatalogRuntimeUnavailable(model: LlmCatalogModelDto): boolean {
  return model.runtimeStatus === "UNAVAILABLE" || model.runtimeStatus === "PROBE_FAILED";
}

export function isCatalogRuntimeNotProbed(model: LlmCatalogModelDto): boolean {
  return model.runtimeStatus === "NOT_PROBED" || model.runtimeStatus === "CONFIGURED";
}

export type CatalogAdminLabels = {
  catalogRuntimeStatusConfigured: string;
  catalogRuntimeStatusNotProbed: string;
  catalogRuntimeStatusNotProbedOpenAI: string;
  catalogRuntimeStatusAvailable: string;
  catalogRuntimeStatusUnavailable: string;
  catalogRuntimeStatusUnavailableOpenAI: string;
  catalogRuntimeStatusProbeFailed: string;
  catalogSourceLitellmConfigured: string;
  catalogSourceConfiguredCatalog: string;
  catalogSourceOllamaLive: string;
  catalogSourceUnknown: string;
  catalogSourceProperties: string;
  catalogRuntimeUnavailable: string;
  catalogRuntimeUnavailableOpenAI: string;
};

export function catalogSourceLabel(source: LlmCatalogSource, labels: CatalogAdminLabels): string {
  switch (source) {
    case "LITELLM_CONFIGURED":
      return labels.catalogSourceLitellmConfigured;
    case "CONFIGURED_CATALOG":
      return labels.catalogSourceConfiguredCatalog;
    case "OLLAMA_LIVE":
      return labels.catalogSourceOllamaLive;
    case "UNKNOWN":
      return labels.catalogSourceUnknown;
    case "PROPERTIES":
      return labels.catalogSourceProperties;
    default:
      return source;
  }
}

export function catalogRuntimeStatusLabel(
  status: LlmCatalogRuntimeStatus,
  provider: LlmProvider,
  labels: CatalogAdminLabels,
): string {
  if (status === "NOT_PROBED" && provider === "OPENAI_COMPATIBLE") {
    return labels.catalogRuntimeStatusNotProbedOpenAI;
  }
  if (status === "UNAVAILABLE" && provider === "OPENAI_COMPATIBLE") {
    return labels.catalogRuntimeStatusUnavailableOpenAI;
  }
  switch (status) {
    case "CONFIGURED":
      return labels.catalogRuntimeStatusConfigured;
    case "NOT_PROBED":
      return labels.catalogRuntimeStatusNotProbed;
    case "AVAILABLE":
      return labels.catalogRuntimeStatusAvailable;
    case "UNAVAILABLE":
      return labels.catalogRuntimeStatusUnavailable;
    case "PROBE_FAILED":
      return labels.catalogRuntimeStatusProbeFailed;
    default:
      return status;
  }
}

export function catalogRuntimeUnavailableMessage(model: LlmCatalogModelDto, labels: CatalogAdminLabels): string {
  if (model.provider === "OPENAI_COMPATIBLE") {
    return labels.catalogRuntimeUnavailableOpenAI;
  }
  return labels.catalogRuntimeUnavailable;
}

export function isCatalogVectorIncompatible(model: LlmCatalogModelDto): boolean {
  return model.capability === "EMBEDDING" && model.compatibleWithCurrentVectorStore === false;
}

/** Embeddings that cannot be used for indexing or Lab evaluation. */
export function isCatalogIndexingDisabled(model: LlmCatalogModelDto): boolean {
  if (model.capability !== "EMBEDDING") {
    return false;
  }
  return isCatalogVectorIncompatible(model) || isCatalogRuntimeUnavailable(model);
}

export function groupCatalogByCapability(models: LlmCatalogModelDto[]) {
  return {
    chat: models.filter((m) => m.capability === "CHAT"),
    embedding: models.filter((m) => m.capability === "EMBEDDING"),
  };
}
