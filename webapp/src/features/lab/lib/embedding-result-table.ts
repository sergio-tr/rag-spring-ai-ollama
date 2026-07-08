import type { ComparisonRow } from "@/features/lab/lib/lab-benchmark-labels";
import { formatMetricNumber } from "@/features/lab/lib/lab-comparison-metrics";

export type EmbeddingItemRow = {
  id: string;
  question: string;
  datasetQuestionId: string;
  embeddingModelId: string;
  expectedGold: string;
  retrievedTop1: string;
  goldRank: number | null;
  hitAt1: number | null;
  hitAt3: number | null;
  hitAt5: number | null;
  topScore: number | null;
  latencyMs: number | null;
  outcome: string;
  answer: string;
  answerModelId: string;
  hasDownstreamAnswer: boolean;
};

export type EmbeddingRecommendation = {
  bestModelId: string;
  bestReasonKey: "benchmarkEmbeddingRecommendQuality";
  bestMetrics: { recallAt1: string; mrr: string; ndcgAt5: string };
  latencyModelId: string | null;
  latencyReasonKey: "benchmarkEmbeddingRecommendLatency" | null;
  latencyMs: string | null;
};

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null;
}

function numberOrNull(value: unknown): number | null {
  if (value == null || value === "NOT_AVAILABLE") return null;
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "boolean") return value ? 1 : 0;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function readStringList(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((entry): entry is string => typeof entry === "string" && entry.trim().length > 0);
  }
  if (typeof value === "string" && value.trim()) {
    const trimmed = value.trim();
    if (trimmed.startsWith("[")) {
      try {
        const parsed = JSON.parse(trimmed) as unknown;
        if (Array.isArray(parsed)) {
          return parsed.filter((entry): entry is string => typeof entry === "string" && entry.trim().length > 0);
        }
      } catch {
        return [];
      }
    }
    return trimmed
      .split(/[;,]/)
      .map((part) => part.trim())
      .filter(Boolean);
  }
  return [];
}

function firstNonBlank(...values: Array<string | null | undefined>): string {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return "";
}

function readMetricNumber(
  mp: Record<string, unknown>,
  ret: Record<string, unknown> | null,
  analysis: Record<string, unknown> | null,
  snake: string,
  camel: string,
): number | null {
  return (
    numberOrNull(ret?.[camel]) ??
    numberOrNull(analysis?.[camel]) ??
    numberOrNull(mp[snake]) ??
    numberOrNull(mp[camel])
  );
}

function formatGoldLabel(chunkIds: string[], documentIds: string[]): string {
  if (chunkIds.length > 0) {
    const preview = chunkIds.slice(0, 2).join(", ");
    return chunkIds.length > 2 ? `${preview} (+${chunkIds.length - 2})` : preview;
  }
  if (documentIds.length > 0) {
    const preview = documentIds.slice(0, 2).join(", ");
    return documentIds.length > 2 ? `${preview} (+${documentIds.length - 2})` : preview;
  }
  return "-";
}

function readTopRetrieved(mp: Record<string, unknown>, chunkIds: string[], documentIds: string[]): string {
  if (chunkIds.length > 0) return chunkIds[0] ?? "-";
  if (documentIds.length > 0) return documentIds[0] ?? "-";
  const retrieved = mp.retrieved;
  if (Array.isArray(retrieved) && retrieved.length > 0) {
    const first = asRecord(retrieved[0]);
    const chunkId = typeof first?.chunk_id === "string" ? first.chunk_id : "";
    const documentId = typeof first?.document_id === "string" ? first.document_id : "";
    return firstNonBlank(chunkId, documentId) || "-";
  }
  const topDoc = typeof mp.top_document_id === "string" ? mp.top_document_id.trim() : "";
  return topDoc || "-";
}

function readTopScore(mp: Record<string, unknown>): number | null {
  const retrieved = mp.retrieved;
  if (Array.isArray(retrieved) && retrieved.length > 0) {
    const first = asRecord(retrieved[0]);
    return numberOrNull(first?.score ?? first?.distance);
  }
  return null;
}

export function toEmbeddingItemRow(row: unknown, idx: number): EmbeddingItemRow {
  const item = asRecord(row);
  const mvp = asRecord(item?.mvp);
  const ret = asRecord(mvp?.retrieval);
  const op = asRecord(mvp?.operational);
  const analysis = asRecord(mvp?.analysis);
  const mp = asRecord(item?.metricsPayload) ?? {};

  const outcomeFromStatus = typeof item?.status === "string" && item.status.trim() ? item.status.trim() : "";
  const outcome =
    typeof op?.outcome === "string" && op.outcome.trim()
      ? op.outcome.trim()
      : typeof mp.item_outcome === "string" && mp.item_outcome.trim()
        ? mp.item_outcome.trim()
        : outcomeFromStatus || "-";

  const embeddingModelId = firstNonBlank(
    typeof op?.embeddingModelId === "string" ? op.embeddingModelId : "",
    typeof item?.embeddingModelId === "string" ? item.embeddingModelId : "",
    typeof mp.embedding_model_id === "string" ? mp.embedding_model_id : "",
  );

  const chunkIds = readStringList(mp.gold_chunk_ids ?? mp.goldChunkIds);
  const documentIds = readStringList(mp.gold_document_ids ?? mp.goldDocumentIds);
  const retrievedChunks = readStringList(mp.retrieved_chunk_ids ?? mp.retrievedChunkIds);
  const retrievedDocs = readStringList(mp.retrieved_document_ids ?? mp.retrievedDocumentIds);

  const goldRank =
    readMetricNumber(mp, ret, analysis, "first_relevant_rank", "firstRelevantRank") ??
    (typeof ret?.goldFound === "boolean" && ret.goldFound === false ? 0 : null);

  const answer = firstNonBlank(
    typeof item?.actualAnswer === "string" ? item.actualAnswer : "",
    typeof item?.answer === "string" ? item.answer : "",
    typeof mp.generated_answer === "string" ? mp.generated_answer : "",
  );

  const answerModelId = firstNonBlank(
    typeof mp.llm_model_id === "string" ? mp.llm_model_id : "",
    typeof item?.llmModelId === "string" ? item.llmModelId : "",
    typeof op?.modelId === "string" ? op.modelId : "",
  );

  return {
    id:
      typeof item?.itemId === "string" && item.itemId
        ? item.itemId
        : typeof item?.id === "string" && item.id
          ? item.id
          : `row-${idx}`,
    question:
      typeof item?.questionText === "string"
        ? item.questionText
        : typeof item?.question === "string"
          ? item.question
          : "",
    datasetQuestionId:
      typeof mvp?.datasetQuestionId === "string"
        ? mvp.datasetQuestionId
        : typeof mp.dataset_question_id === "string"
          ? mp.dataset_question_id
          : "",
    embeddingModelId: embeddingModelId || "-",
    expectedGold: formatGoldLabel(chunkIds, documentIds),
    retrievedTop1: readTopRetrieved(mp, retrievedChunks, retrievedDocs),
    goldRank,
    hitAt1: readMetricNumber(mp, ret, analysis, "recall_at_1", "recallAt1"),
    hitAt3: readMetricNumber(mp, ret, analysis, "recall_at_3", "recallAt3"),
    hitAt5: readMetricNumber(mp, ret, analysis, "recall_at_5", "recallAt5"),
    topScore: readTopScore(mp),
    latencyMs: numberOrNull(op?.latencyMs ?? item?.latencyMs ?? mp.latency_ms),
    outcome,
    answer,
    answerModelId,
    hasDownstreamAnswer: answer.trim().length > 0 && answerModelId.trim().length > 0,
  };
}

export function formatHitIndicator(value: number | null): string {
  if (value == null || value === 0) return "Miss";
  if (value >= 1) return "Hit";
  return formatMetricNumber(value, 2);
}

export function embeddingRowMatchesHitFilter(row: EmbeddingItemRow, filter: string): boolean {
  if (filter === "ALL") return true;
  const hit = (row.hitAt1 ?? 0) >= 1 || (row.goldRank != null && row.goldRank > 0);
  return filter === "HIT" ? hit : !hit;
}

export function embeddingRowMatchesGoldFilter(row: EmbeddingItemRow, filter: string): boolean {
  const needle = filter.trim().toLowerCase();
  if (!needle) return true;
  return row.expectedGold.toLowerCase().includes(needle) || row.retrievedTop1.toLowerCase().includes(needle);
}

export function embeddingRowMatchesQueryFilter(row: EmbeddingItemRow, filter: string): boolean {
  const needle = filter.trim().toLowerCase();
  if (!needle) return true;
  return (
    row.question.toLowerCase().includes(needle) ||
    row.datasetQuestionId.toLowerCase().includes(needle) ||
    row.id.toLowerCase().includes(needle)
  );
}

function retrievalScore(row: ComparisonRow): number {
  const r1 = typeof row.meanRecallAt1 === "number" ? row.meanRecallAt1 : 0;
  const mrr = typeof row.meanMrr === "number" ? row.meanMrr : 0;
  const ndcg = typeof row.meanNdcgAt5 === "number" ? row.meanNdcgAt5 : 0;
  return r1 * 3 + mrr * 2 + ndcg;
}

export function sortEmbeddingComparisonRows(rows: ComparisonRow[]): ComparisonRow[] {
  return [...rows].sort((a, b) => retrievalScore(b) - retrievalScore(a));
}

export function sortEmbeddingItemRows(rows: EmbeddingItemRow[]): EmbeddingItemRow[] {
  return [...rows].sort((a, b) => {
    const av = a.hitAt1 ?? -1;
    const bv = b.hitAt1 ?? -1;
    if (bv !== av) return bv - av;
    const ar = a.goldRank ?? Number.MAX_SAFE_INTEGER;
    const br = b.goldRank ?? Number.MAX_SAFE_INTEGER;
    return ar - br;
  });
}

export function recommendEmbeddingModel(rows: ComparisonRow[]): EmbeddingRecommendation | null {
  const candidates = rows.filter((row) => {
    const label =
      typeof row.embeddingModelId === "string" && row.embeddingModelId.trim()
        ? row.embeddingModelId.trim()
        : typeof row.comparisonLabel === "string"
          ? row.comparisonLabel.trim()
          : "";
    return label.length > 0 && (row.executed ?? 0) > 0;
  });
  if (candidates.length === 0) return null;

  const ranked = sortEmbeddingComparisonRows(candidates);
  const best = ranked[0];
  if (!best) return null;

  const bestModelId =
    typeof best.embeddingModelId === "string" && best.embeddingModelId.trim()
      ? best.embeddingModelId.trim()
      : typeof best.comparisonLabel === "string"
        ? best.comparisonLabel.trim()
        : "";

  const latencySorted = [...candidates].sort((a, b) => {
    const av = typeof a.meanLatencyMs === "number" ? a.meanLatencyMs : Number.MAX_SAFE_INTEGER;
    const bv = typeof b.meanLatencyMs === "number" ? b.meanLatencyMs : Number.MAX_SAFE_INTEGER;
    return av - bv;
  });

  const latencyAlternative = latencySorted.find((row) => {
    const id =
      typeof row.embeddingModelId === "string" && row.embeddingModelId.trim()
        ? row.embeddingModelId.trim()
        : typeof row.comparisonLabel === "string"
          ? row.comparisonLabel.trim()
          : "";
    if (!id || id === bestModelId || typeof row.meanLatencyMs !== "number") return false;
    const recall = typeof row.meanRecallAt1 === "number" ? row.meanRecallAt1 : 0;
    const bestRecall = typeof best.meanRecallAt1 === "number" ? best.meanRecallAt1 : 0;
    return bestRecall === 0 || recall >= bestRecall * 0.85;
  });
  const latencyModelId =
    latencyAlternative &&
    (typeof latencyAlternative.embeddingModelId === "string"
      ? latencyAlternative.embeddingModelId.trim()
      : typeof latencyAlternative.comparisonLabel === "string"
        ? latencyAlternative.comparisonLabel.trim()
        : "");

  return {
    bestModelId,
    bestReasonKey: "benchmarkEmbeddingRecommendQuality",
    bestMetrics: {
      recallAt1: formatMetricNumber(best.meanRecallAt1),
      mrr: formatMetricNumber(best.meanMrr),
      ndcgAt5: formatMetricNumber(best.meanNdcgAt5),
    },
    latencyModelId: latencyModelId || null,
    latencyReasonKey: latencyModelId ? "benchmarkEmbeddingRecommendLatency" : null,
    latencyMs:
      latencyAlternative && typeof latencyAlternative.meanLatencyMs === "number"
        ? formatLatencyRounded(latencyAlternative.meanLatencyMs)
        : null,
  };
}

function formatLatencyRounded(value: number): string {
  return `${Math.round(value)}`;
}
