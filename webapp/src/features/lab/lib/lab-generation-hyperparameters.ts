import type {
  LabBenchmarkRuntimeParameters,
  LabEvaluationDraftKind,
} from "@/features/lab/lib/lab-evaluation-draft";

export type LabResponseFormat = "text" | "json_object" | "json_schema";

const GENERATION_KEYS = new Set([
  "temperature",
  "topP",
  "seed",
  "maxTokens",
  "presencePenalty",
  "frequencyPenalty",
  "responseFormat",
  "stop",
  "think",
]);

export function isGenerationHyperparameterKey(key: string): boolean {
  return GENERATION_KEYS.has(key);
}

export function clampNumber(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

export function parseStopSequencesText(text: string): string[] {
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

export function formatStopSequencesText(stop: string[] | undefined): string {
  return (stop ?? []).join("\n");
}

export function buildBenchmarkRuntimeParametersPayload(
  benchmarkKind: LabEvaluationDraftKind,
  value: LabBenchmarkRuntimeParameters,
): Record<string, unknown> | undefined {
  const out: Record<string, unknown> = {};

  const includeGeneration = benchmarkKind === "LLM_JUDGE_QA";
  const includeRetrieval =
    benchmarkKind === "EMBEDDING_RETRIEVAL" || benchmarkKind === "RAG_PRESET_END_TO_END";

  if (includeGeneration) {
    if (typeof value.temperature === "number" && Number.isFinite(value.temperature)) {
      out.temperature = clampNumber(value.temperature, 0, 2);
    }
    if (typeof value.topP === "number" && Number.isFinite(value.topP)) {
      out.top_p = clampNumber(value.topP, 0, 1);
    }
    if (typeof value.seed === "number" && Number.isFinite(value.seed)) {
      out.seed = Math.trunc(value.seed);
    }
    if (typeof value.maxTokens === "number" && Number.isFinite(value.maxTokens)) {
      out.max_tokens = Math.max(1, Math.trunc(value.maxTokens));
    }
    if (typeof value.presencePenalty === "number" && Number.isFinite(value.presencePenalty)) {
      out.presence_penalty = clampNumber(value.presencePenalty, -2, 2);
    }
    if (typeof value.frequencyPenalty === "number" && Number.isFinite(value.frequencyPenalty)) {
      out.frequency_penalty = clampNumber(value.frequencyPenalty, -2, 2);
    }
    if (value.responseFormat === "json_object") {
      out.response_format = { type: "json_object" };
    }
    if (Array.isArray(value.stop) && value.stop.length > 0) {
      out.stop = value.stop.map((s) => s.trim()).filter((s) => s.length > 0);
    }
    if (value.think === true) {
      out.think = true;
    }
    if (typeof value.secondaryLlmModelId === "string" && value.secondaryLlmModelId.trim()) {
      out.secondaryLlmModelId = value.secondaryLlmModelId.trim();
    }
  }

  if (includeRetrieval) {
    if (typeof value.topK === "number" && Number.isFinite(value.topK)) {
      out.topK = Math.max(1, Math.trunc(value.topK));
    }
    if (typeof value.similarityThreshold === "number" && Number.isFinite(value.similarityThreshold)) {
      out.similarityThreshold = clampNumber(value.similarityThreshold, 0, 1);
    }
  }

  return Object.keys(out).length > 0 ? out : undefined;
}
