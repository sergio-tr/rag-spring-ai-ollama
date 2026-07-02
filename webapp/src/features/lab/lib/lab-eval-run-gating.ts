import type { BenchmarkKind } from "@/types/api";

export type CorpusRunGateInput = {
  needsEvaluationCorpus: boolean;
  hasEvaluationCorpus: boolean;
  corpusRunnable: boolean;
  corpusProcessing: boolean;
  corpusReady: boolean;
  corpusIndexReady: boolean;
  preparingIndex: boolean;
  primaryBlocker: string | null | undefined;
  documentCentricCorpus: boolean;
};

/** True when corpus readiness should hard-disable the Run button. */
export function isCorpusBlockingRun(input: CorpusRunGateInput): boolean {
  if (!input.needsEvaluationCorpus) {
    return false;
  }
  if (!input.hasEvaluationCorpus) {
    return true;
  }
  if (!input.corpusRunnable) {
    return true;
  }
  if (input.corpusProcessing) {
    return true;
  }
  if (input.primaryBlocker) {
    return true;
  }
  if (input.documentCentricCorpus) {
    return false;
  }
  return input.preparingIndex || (input.corpusReady && !input.corpusIndexReady);
}

export function isDocumentCentricCorpusBenchmark(kind: BenchmarkKind): boolean {
  return kind === "RAG_PRESET_END_TO_END" || kind === "EMBEDDING_RETRIEVAL";
}

export function selectedEmbeddingModelCount(
  embeddingModelIds: readonly string[],
  embeddingModelId: string,
): number {
  const fromList = embeddingModelIds.map((x) => x.trim()).filter(Boolean).length;
  if (fromList > 0) {
    return fromList;
  }
  return embeddingModelId.trim() ? 1 : 0;
}

export function selectedLlmModelCount(llmModelIds: readonly string[], llmModelId: string): number {
  const fromList = llmModelIds.map((x) => x.trim()).filter(Boolean).length;
  if (fromList > 0) {
    return fromList;
  }
  return llmModelId.trim() ? 1 : 0;
}
