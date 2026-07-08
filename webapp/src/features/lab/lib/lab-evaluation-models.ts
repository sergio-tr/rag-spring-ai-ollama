import type { LabEvaluationModelDto } from "@/types/api";

/** Thesis evaluation defaults when present in the configured catalog. */
export const THESIS_DEFAULT_EMBEDDING_MODEL_ID = "bge-m3";
export const THESIS_DEFAULT_PRIMARY_LLM_MODEL_ID = "qwen3.5:9b";
export const THESIS_DEFAULT_SECONDARY_LLM_MODEL_ID = "gemma4:12b";

function firstSelectableModelName(
  models: LabEvaluationModelDto[],
  predicate: (model: LabEvaluationModelDto) => boolean,
): string | null {
  const row = models.find((m) => m.evalSelectable && predicate(m));
  return row?.modelName ?? null;
}

export function selectableEvalModelNames(models: LabEvaluationModelDto[]): string[] {
  return models
    .filter((m) => m.evalSelectable)
    .map((m) => m.modelName)
    .sort((a, b) => a.localeCompare(b));
}

export function allEvalModelNames(models: LabEvaluationModelDto[]): string[] {
  return models.map((m) => m.modelName).sort((a, b) => a.localeCompare(b));
}

export function compatibleEmbeddingEvalModelNames(models: LabEvaluationModelDto[]): string[] {
  return models
    .filter((m) => m.compatibleWithCurrentVectorStore === true)
    .map((m) => m.modelName)
    .sort((a, b) => a.localeCompare(b));
}

export function defaultEmbeddingModelId(models: LabEvaluationModelDto[]): string | null {
  const compatible = models.filter((m) => m.compatibleWithCurrentVectorStore === true);
  const thesis = compatible.find(
    (m) => m.evalSelectable && m.modelName === THESIS_DEFAULT_EMBEDDING_MODEL_ID,
  );
  if (thesis) return thesis.modelName;
  const preferred = compatible.find((m) => m.usableAsDefault && m.evalSelectable);
  if (preferred) return preferred.modelName;
  const firstSelectable = compatible.find((m) => m.evalSelectable);
  return firstSelectable?.modelName ?? null;
}

export function defaultLlmModelId(models: LabEvaluationModelDto[]): string | null {
  const thesis = firstSelectableModelName(models, (m) => m.modelName === THESIS_DEFAULT_PRIMARY_LLM_MODEL_ID);
  if (thesis) return thesis;
  const preferred = firstSelectableModelName(models, (m) => m.usableAsDefault === true);
  if (preferred) return preferred;
  return firstSelectableModelName(models, () => true);
}

export function defaultSecondaryLlmModelId(
  models: LabEvaluationModelDto[],
  primaryLlmModelId?: string | null,
): string | null {
  const primary = primaryLlmModelId?.trim() ?? "";
  const thesis = firstSelectableModelName(
    models,
    (m) => m.modelName === THESIS_DEFAULT_SECONDARY_LLM_MODEL_ID && m.modelName !== primary,
  );
  if (thesis) return thesis;
  const alternate = models.find((m) => m.evalSelectable && m.modelName !== primary);
  return alternate?.modelName ?? null;
}

export function isEmbeddingModelEvalSelectable(
  models: LabEvaluationModelDto[],
  modelName: string,
): boolean {
  const row = models.find((m) => m.modelName === modelName);
  return row?.evalSelectable === true;
}

export type LabCatalogProvider = "OLLAMA_NATIVE" | "OPENAI_COMPATIBLE";

export function labComparisonBlockedMessageKey(
  capability: "CHAT" | "EMBEDDING",
  provider?: LabCatalogProvider | null,
): string {
  if (provider === "OPENAI_COMPATIBLE") {
    return capability === "CHAT"
      ? "labLlmBlockedByModelAvailabilityOpenAI"
      : "labEmbeddingBlockedByModelAvailabilityOpenAI";
  }
  return capability === "CHAT"
    ? "labLlmBlockedByModelAvailability"
    : "labEmbeddingBlockedByModelAvailability";
}

export function labDraftInvalidModelMessageKey(
  kind: "llm" | "llmList" | "embedding" | "embeddingList",
  provider?: LabCatalogProvider | null,
): string {
  const openAi = provider === "OPENAI_COMPATIBLE";
  switch (kind) {
    case "llm":
      return openAi ? "evalDraftWarnLlmInvalidOpenAI" : "evalDraftWarnLlmInvalid";
    case "llmList":
      return openAi ? "evalDraftWarnLlmListInvalidOpenAI" : "evalDraftWarnLlmListInvalid";
    case "embedding":
      return openAi ? "evalDraftWarnEmbeddingInvalidOpenAI" : "evalDraftWarnEmbeddingInvalid";
    case "embeddingList":
      return openAi ? "evalDraftWarnEmbeddingListInvalidOpenAI" : "evalDraftWarnEmbeddingListInvalid";
  }
}
