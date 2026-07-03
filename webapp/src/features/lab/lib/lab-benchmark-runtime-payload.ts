import type { LabEvaluationDraftKind } from "@/features/lab/lib/lab-evaluation-draft";
import type { LabEmbeddingRuntimeParameters } from "@/features/lab/lib/lab-embedding-hyperparameters";
import { buildEmbeddingBenchmarkRuntimeParametersPayload } from "@/features/lab/lib/lab-embedding-hyperparameters";
import {
  buildBenchmarkRuntimeParametersPayload,
  isGenerationHyperparameterKey,
} from "@/features/lab/lib/lab-generation-hyperparameters";

const GENERATION_PAYLOAD_KEYS = new Set([
  "temperature",
  "top_p",
  "topP",
  "seed",
  "max_tokens",
  "maxTokens",
  "presence_penalty",
  "presencePenalty",
  "frequency_penalty",
  "frequencyPenalty",
  "response_format",
  "responseFormat",
  "stop",
  "think",
  "secondaryLlmModelId",
]);

function stripGenerationKeys(out: Record<string, unknown>): void {
  for (const key of Object.keys(out)) {
    if (isGenerationHyperparameterKey(key) || GENERATION_PAYLOAD_KEYS.has(key)) {
      delete out[key];
    }
  }
}

/** Merges generation and embedding runtime parameter groups for Lab benchmark POST bodies. */
export function buildLabBenchmarkRuntimeParametersPayload(
  benchmarkKind: LabEvaluationDraftKind,
  value: LabEmbeddingRuntimeParameters,
): Record<string, unknown> | undefined {
  const includeGeneration = benchmarkKind === "LLM_JUDGE_QA";
  const generation = includeGeneration
    ? buildBenchmarkRuntimeParametersPayload(benchmarkKind, value) ?? {}
    : {};
  const includeEmbedding =
    benchmarkKind === "EMBEDDING_RETRIEVAL" || benchmarkKind === "RAG_PRESET_END_TO_END";
  const embedding = includeEmbedding
    ? buildEmbeddingBenchmarkRuntimeParametersPayload(value) ?? {}
    : {};

  const out: Record<string, unknown> = { ...generation };
  for (const [key, nested] of Object.entries(embedding)) {
    if (nested !== undefined && nested !== null) {
      out[key] = nested;
    }
  }
  if (!includeGeneration) {
    stripGenerationKeys(out);
  }
  return Object.keys(out).length > 0 ? out : undefined;
}
