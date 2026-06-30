import type { LabEvaluationModelDto } from "@/types/api";

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
  const preferred = compatible.find((m) => m.usableAsDefault && m.evalSelectable);
  if (preferred) return preferred.modelName;
  const firstSelectable = compatible.find((m) => m.evalSelectable);
  return firstSelectable?.modelName ?? null;
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
