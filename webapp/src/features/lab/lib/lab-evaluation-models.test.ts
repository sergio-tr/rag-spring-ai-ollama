import { describe, expect, it } from "vitest";
import {
  compatibleEmbeddingEvalModelNames,
  defaultEmbeddingModelId,
  defaultLlmModelId,
  defaultSecondaryLlmModelId,
  labComparisonBlockedMessageKey,
  labDraftInvalidModelMessageKey,
  selectableEvalModelNames,
  THESIS_DEFAULT_EMBEDDING_MODEL_ID,
  THESIS_DEFAULT_PRIMARY_LLM_MODEL_ID,
  THESIS_DEFAULT_SECONDARY_LLM_MODEL_ID,
} from "./lab-evaluation-models";
import type { LabEvaluationModelDto } from "@/types/api";

const models: LabEvaluationModelDto[] = [
  {
    modelName: "good:latest",
    evalSelectable: true,
    blockedReason: null,
    blockedReasonCode: null,
    runtimeStatus: "AVAILABLE",
    embeddingDimensions: 1024,
    compatibleWithCurrentVectorStore: true,
    usableAsDefault: true,
  },
  {
    modelName: "bad:latest",
    evalSelectable: false,
    blockedReason: "Incompatible with vector store",
    blockedReasonCode: "EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE",
    runtimeStatus: "AVAILABLE",
    embeddingDimensions: 768,
    compatibleWithCurrentVectorStore: false,
    usableAsDefault: false,
  },
];

describe("lab-evaluation-models", () => {
  it("selectableEvalModelNames filters evalSelectable rows", () => {
    expect(selectableEvalModelNames(models)).toEqual(["good:latest"]);
  });

  it("compatibleEmbeddingEvalModelNames keeps vector-store-compatible tags", () => {
    expect(compatibleEmbeddingEvalModelNames(models)).toEqual(["good:latest"]);
  });

  it("defaultEmbeddingModelId prefers usableAsDefault compatible model", () => {
    expect(defaultEmbeddingModelId(models)).toBe("good:latest");
  });

  it("defaultEmbeddingModelId prefers thesis default when present", () => {
    const thesisModels: LabEvaluationModelDto[] = [
      ...models,
      {
        modelName: THESIS_DEFAULT_EMBEDDING_MODEL_ID,
        evalSelectable: true,
        blockedReason: null,
        blockedReasonCode: null,
        runtimeStatus: "AVAILABLE",
        embeddingDimensions: 1024,
        compatibleWithCurrentVectorStore: true,
        usableAsDefault: false,
      },
    ];
    expect(defaultEmbeddingModelId(thesisModels)).toBe(THESIS_DEFAULT_EMBEDDING_MODEL_ID);
  });

  it("defaultLlmModelId prefers thesis primary when present", () => {
    const chat: LabEvaluationModelDto[] = [
      {
        modelName: "other",
        evalSelectable: true,
        blockedReason: null,
        blockedReasonCode: null,
        runtimeStatus: "AVAILABLE",
        embeddingDimensions: null,
        compatibleWithCurrentVectorStore: null,
        usableAsDefault: true,
      },
      {
        modelName: THESIS_DEFAULT_PRIMARY_LLM_MODEL_ID,
        evalSelectable: true,
        blockedReason: null,
        blockedReasonCode: null,
        runtimeStatus: "AVAILABLE",
        embeddingDimensions: null,
        compatibleWithCurrentVectorStore: null,
        usableAsDefault: false,
      },
    ];
    expect(defaultLlmModelId(chat)).toBe(THESIS_DEFAULT_PRIMARY_LLM_MODEL_ID);
  });

  it("defaultSecondaryLlmModelId prefers thesis secondary and excludes primary", () => {
    const chat: LabEvaluationModelDto[] = [
      {
        modelName: THESIS_DEFAULT_PRIMARY_LLM_MODEL_ID,
        evalSelectable: true,
        blockedReason: null,
        blockedReasonCode: null,
        runtimeStatus: "AVAILABLE",
        embeddingDimensions: null,
        compatibleWithCurrentVectorStore: null,
        usableAsDefault: true,
      },
      {
        modelName: THESIS_DEFAULT_SECONDARY_LLM_MODEL_ID,
        evalSelectable: true,
        blockedReason: null,
        blockedReasonCode: null,
        runtimeStatus: "AVAILABLE",
        embeddingDimensions: null,
        compatibleWithCurrentVectorStore: null,
        usableAsDefault: false,
      },
    ];
    expect(defaultSecondaryLlmModelId(chat, THESIS_DEFAULT_PRIMARY_LLM_MODEL_ID)).toBe(
      THESIS_DEFAULT_SECONDARY_LLM_MODEL_ID,
    );
  });

  it("labComparisonBlockedMessageKey is provider-aware", () => {
    expect(labComparisonBlockedMessageKey("CHAT", "OPENAI_COMPATIBLE")).toBe(
      "labLlmBlockedByModelAvailabilityOpenAI",
    );
    expect(labComparisonBlockedMessageKey("CHAT", "OLLAMA_NATIVE")).toBe(
      "labLlmBlockedByModelAvailability",
    );
    expect(labComparisonBlockedMessageKey("EMBEDDING", "OPENAI_COMPATIBLE")).toBe(
      "labEmbeddingBlockedByModelAvailabilityOpenAI",
    );
  });

  it("labDraftInvalidModelMessageKey avoids Ollama tag copy for OpenAI-compatible", () => {
    expect(labDraftInvalidModelMessageKey("llm", "OPENAI_COMPATIBLE")).toBe(
      "evalDraftWarnLlmInvalidOpenAI",
    );
    expect(labDraftInvalidModelMessageKey("llm", "OLLAMA_NATIVE")).toBe("evalDraftWarnLlmInvalid");
    expect(labDraftInvalidModelMessageKey("embeddingList", "OPENAI_COMPATIBLE")).toBe(
      "evalDraftWarnEmbeddingListInvalidOpenAI",
    );
  });
});
