import type { LabEvaluationDraftKind } from "@/features/lab/lib/lab-evaluation-draft";
import type { LabEmbeddingRuntimeParameters } from "@/features/lab/lib/lab-embedding-hyperparameters";
import { buildEmbeddingBenchmarkRuntimeParametersPayload } from "@/features/lab/lib/lab-embedding-hyperparameters";
import { buildBenchmarkRuntimeParametersPayload } from "@/features/lab/lib/lab-generation-hyperparameters";

/** Merges generation and embedding runtime parameter groups for Lab benchmark POST bodies. */
export function buildLabBenchmarkRuntimeParametersPayload(
  benchmarkKind: LabEvaluationDraftKind,
  value: LabEmbeddingRuntimeParameters,
): Record<string, unknown> | undefined {
  const generation = buildBenchmarkRuntimeParametersPayload(benchmarkKind, value) ?? {};
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
  return Object.keys(out).length > 0 ? out : undefined;
}
