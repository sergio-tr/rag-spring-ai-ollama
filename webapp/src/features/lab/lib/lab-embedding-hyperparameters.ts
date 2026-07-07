import type { LabBenchmarkRuntimeParameters } from "@/features/lab/lib/lab-evaluation-draft";
import type { LabEvaluationModelDto } from "@/types/api";

export type EmbeddingEncodingFormat = "float" | "base64";

export type LabEmbeddingRuntimeParameters = LabBenchmarkRuntimeParameters & {
  encodingFormat?: EmbeddingEncodingFormat;
  dimensions?: number;
  timeoutSeconds?: number;
  batchSize?: number;
  maxInputChars?: number;
  normalize?: boolean;
  truncate?: string;
  materializationStrategy?: string;
};

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
  "secondaryLlmModelId",
]);

export function isEmbeddingHyperparameterKey(key: string): boolean {
  return !GENERATION_KEYS.has(key);
}

export function aggregateEmbeddingCapabilities(models: LabEvaluationModelDto[]): {
  supportsEncodingFormat: boolean;
  supportedEncodingFormats: EmbeddingEncodingFormat[];
  supportsDimensions: boolean;
  supportsNormalize: boolean;
  supportsTruncate: boolean;
  defaultDimensions: number | null;
  maxInputTokens: number | null;
} {
  const selected = models.filter((m) => m.evalSelectable);
  const pool = selected.length > 0 ? selected : models;
  const supportsEncodingFormat = pool.some((m) => m.supportsEncodingFormat === true);
  const supportedEncodingFormats = Array.from(
    new Set(
      pool.flatMap((m) =>
        (m.supportedEncodingFormats ?? []).filter(
          (f): f is EmbeddingEncodingFormat => f === "float" || f === "base64",
        ),
      ),
    ),
  );
  const supportsDimensions = pool.some((m) => m.supportsDimensions === true);
  const supportsNormalize = pool.some((m) => m.supportsNormalize === true);
  const supportsTruncate = pool.some((m) => m.supportsTruncate === true);
  const defaultDimensions =
    pool.find((m) => typeof m.defaultDimensions === "number")?.defaultDimensions ?? null;
  const maxInputTokens = pool.find((m) => typeof m.maxInputTokens === "number")?.maxInputTokens ?? null;
  return {
    supportsEncodingFormat,
    supportedEncodingFormats:
      supportedEncodingFormats.length > 0 ? supportedEncodingFormats : ["float", "base64"],
    supportsDimensions,
    supportsNormalize,
    supportsTruncate,
    defaultDimensions,
    maxInputTokens,
  };
}

export function buildEmbeddingBenchmarkRuntimeParametersPayload(
  value: LabEmbeddingRuntimeParameters,
): Record<string, unknown> | undefined {
  const embeddingOptions: Record<string, unknown> = {};
  if (value.encodingFormat) embeddingOptions.encodingFormat = value.encodingFormat;
  if (typeof value.dimensions === "number" && Number.isFinite(value.dimensions)) {
    embeddingOptions.dimensions = Math.trunc(value.dimensions);
  }
  if (typeof value.timeoutSeconds === "number" && Number.isFinite(value.timeoutSeconds)) {
    embeddingOptions.timeoutSeconds = Math.max(1, Math.trunc(value.timeoutSeconds));
  }

  const retrievalOptions: Record<string, unknown> = {};
  if (typeof value.topK === "number" && Number.isFinite(value.topK)) {
    retrievalOptions.topK = Math.max(1, Math.trunc(value.topK));
  }
  if (typeof value.similarityThreshold === "number" && Number.isFinite(value.similarityThreshold)) {
    retrievalOptions.similarityThreshold = Math.min(1, Math.max(0, value.similarityThreshold));
  }
  if (typeof value.materializationStrategy === "string" && value.materializationStrategy.trim()) {
    retrievalOptions.materializationStrategy = value.materializationStrategy.trim();
  }

  const indexingOptions: Record<string, unknown> = {};
  if (typeof value.batchSize === "number" && Number.isFinite(value.batchSize)) {
    indexingOptions.batchSize = Math.max(1, Math.trunc(value.batchSize));
  }
  if (typeof value.maxInputChars === "number" && Number.isFinite(value.maxInputChars)) {
    indexingOptions.maxInputChars = Math.max(64, Math.trunc(value.maxInputChars));
  }
  if (value.normalize === true) indexingOptions.normalize = true;
  if (typeof value.truncate === "string" && value.truncate.trim()) {
    indexingOptions.truncate = value.truncate.trim();
  }

  const out: Record<string, unknown> = {};
  if (Object.keys(embeddingOptions).length > 0) out.embeddingOptions = embeddingOptions;
  if (Object.keys(retrievalOptions).length > 0) out.retrievalOptions = retrievalOptions;
  if (Object.keys(indexingOptions).length > 0) out.indexingOptions = indexingOptions;
  if (retrievalOptions.topK != null) out.topK = retrievalOptions.topK;
  if (retrievalOptions.similarityThreshold != null) {
    out.similarityThreshold = retrievalOptions.similarityThreshold;
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

export function parseEmbeddingBenchmarkRuntimeParameters(raw: unknown): LabEmbeddingRuntimeParameters {
  if (!raw || typeof raw !== "object") return {};
  const src = raw as Record<string, unknown>;
  const out: LabEmbeddingRuntimeParameters = {};
  const embedding = (src.embeddingOptions ?? {}) as Record<string, unknown>;
  const retrieval = (src.retrievalOptions ?? {}) as Record<string, unknown>;
  const indexing = (src.indexingOptions ?? {}) as Record<string, unknown>;

  const encodingFormat = (embedding.encodingFormat ?? embedding.encoding_format ?? src.encodingFormat) as unknown;
  if (encodingFormat === "float" || encodingFormat === "base64") out.encodingFormat = encodingFormat;

  const dimensions = readNumber(embedding, src, "dimensions");
  if (dimensions != null) out.dimensions = Math.trunc(dimensions);

  const timeoutSeconds = readNumber(embedding, src, "timeoutSeconds", "timeout_seconds");
  if (timeoutSeconds != null) out.timeoutSeconds = Math.trunc(timeoutSeconds);

  const topK = readNumber(retrieval, src, "topK", "top_k");
  if (topK != null) out.topK = Math.trunc(topK);

  const similarityThreshold = readNumber(retrieval, src, "similarityThreshold", "similarity_threshold");
  if (similarityThreshold != null) out.similarityThreshold = similarityThreshold;

  const materializationStrategy = readString(retrieval, src, "materializationStrategy", "materialization_strategy");
  if (materializationStrategy) out.materializationStrategy = materializationStrategy;

  const batchSize = readNumber(indexing, src, "batchSize", "batch_size");
  if (batchSize != null) out.batchSize = Math.trunc(batchSize);

  const maxInputChars = readNumber(indexing, src, "maxInputChars", "max_input_chars");
  if (maxInputChars != null) out.maxInputChars = Math.trunc(maxInputChars);

  const normalize = indexing.normalize ?? src.normalize;
  if (normalize === true) out.normalize = true;

  const truncate = readString(indexing, src, "truncate");
  if (truncate) out.truncate = truncate;

  return out;
}

function readNumber(nested: Record<string, unknown>, flat: Record<string, unknown>, ...keys: string[]): number | undefined {
  for (const key of keys) {
    const raw = nested[key] ?? flat[key];
    if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  }
  return undefined;
}

function readString(nested: Record<string, unknown>, flat: Record<string, unknown>, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const raw = nested[key] ?? flat[key];
    if (typeof raw === "string" && raw.trim()) return raw.trim();
  }
  return undefined;
}
