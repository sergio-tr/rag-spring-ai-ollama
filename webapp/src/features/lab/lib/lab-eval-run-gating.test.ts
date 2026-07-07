import { describe, expect, it } from "vitest";
import {
  isCorpusBlockingRun,
  isDocumentCentricCorpusBenchmark,
  selectedEmbeddingModelCount,
} from "./lab-eval-run-gating";

describe("lab-eval-run-gating", () => {
  it("treats embedding retrieval as document-centric corpus benchmark", () => {
    expect(isDocumentCentricCorpusBenchmark("EMBEDDING_RETRIEVAL")).toBe(true);
    expect(isDocumentCentricCorpusBenchmark("RAG_PRESET_END_TO_END")).toBe(true);
    expect(isDocumentCentricCorpusBenchmark("LLM_JUDGE_QA")).toBe(false);
  });

  it("does not block document-centric runs when index is missing but corpus is runnable", () => {
    expect(
      isCorpusBlockingRun({
        needsEvaluationCorpus: true,
        hasEvaluationCorpus: true,
        corpusRunnable: true,
        corpusProcessing: false,
        corpusReady: true,
        corpusIndexReady: false,
        preparingIndex: false,
        primaryBlocker: null,
        documentCentricCorpus: true,
      }),
    ).toBe(false);
  });

  it("blocks non-document-centric runs when index is not ready", () => {
    expect(
      isCorpusBlockingRun({
        needsEvaluationCorpus: true,
        hasEvaluationCorpus: true,
        corpusRunnable: true,
        corpusProcessing: false,
        corpusReady: true,
        corpusIndexReady: false,
        preparingIndex: false,
        primaryBlocker: null,
        documentCentricCorpus: false,
      }),
    ).toBe(true);
  });

  it("counts embedding models from list or single id", () => {
    expect(selectedEmbeddingModelCount([], "")).toBe(0);
    expect(selectedEmbeddingModelCount(["a"], "")).toBe(1);
    expect(selectedEmbeddingModelCount([], "b")).toBe(1);
    expect(selectedEmbeddingModelCount(["a", "b"], "c")).toBe(2);
  });
});
