import type { ComparisonRow } from "@/features/lab/lib/lab-benchmark-labels";

export type ComparisonMetricEnrichment = {
  meanRecallAt1?: number | null;
  meanRecallAt3?: number | null;
  meanRecallAt5?: number | null;
  meanMrr?: number | null;
  meanNdcgAt5?: number | null;
  meanCorrectness?: number | null;
  meanFaithfulness?: number | null;
  meanHallucinationRate?: number | null;
  meanSemanticScore?: number | null;
  containsExpectedAnswerRate?: number | null;
  meanLatencyMs?: number | null;
  p95LatencyMs?: number | null;
  errorCount?: number;
  timeoutCount?: number;
  emptyContentCount?: number;
  noContextCount?: number;
};

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null;
}

function numberOrNull(value: unknown): number | null {
  if (value == null || value === "NOT_AVAILABLE") return null;
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "boolean") return value ? 1 : 0;
  return null;
}

function mean(values: number[]): number | null {
  if (values.length === 0) return null;
  return values.reduce((sum, v) => sum + v, 0) / values.length;
}

function p95(values: number[]): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const idx = Math.min(sorted.length - 1, Math.ceil(sorted.length * 0.95) - 1);
  return sorted[Math.max(0, idx)];
}

function readRunId(item: Record<string, unknown>): string {
  const candidates = [item.childRunId, item.runId];
  for (const c of candidates) {
    if (typeof c === "string" && c.trim()) return c.trim();
  }
  const mvp = asRecord(item.mvp);
  const op = asRecord(mvp?.operational);
  if (typeof op?.runId === "string" && op.runId.trim()) return op.runId.trim();
  return "";
}

function isTimeoutOutcome(outcome: string, failureCode: string, skipCode: string): boolean {
  const blob = `${outcome} ${failureCode} ${skipCode}`.toUpperCase();
  return blob.includes("TIMEOUT");
}

function isEmptyContent(item: Record<string, unknown>): boolean {
  const answer =
    typeof item.actualAnswer === "string"
      ? item.actualAnswer
      : typeof item.answer === "string"
        ? item.answer
        : "";
  return answer.trim().length === 0;
}

function isNoContext(item: Record<string, unknown>): boolean {
  const mvp = asRecord(item.mvp);
  const analysis = asRecord(mvp?.analysis);
  const source = typeof analysis?.sourceCoverageStatus === "string" ? analysis.sourceCoverageStatus : "";
  const retrieval = typeof analysis?.retrievalCoverageStatus === "string" ? analysis.retrievalCoverageStatus : "";
  return source === "NO_CONTEXT" || retrieval === "NO_CONTEXT";
}

export function aggregateRunMetricsFromItems(items: unknown[], runId: string): ComparisonMetricEnrichment {
  const recall1: number[] = [];
  const recall3: number[] = [];
  const recall5: number[] = [];
  const mrrs: number[] = [];
  const ndcgs: number[] = [];
  const correctness: number[] = [];
  const faithfulness: number[] = [];
  const hallucination: number[] = [];
  const semantic: number[] = [];
  const containsExpected: number[] = [];
  const latencies: number[] = [];
  let errorCount = 0;
  let timeoutCount = 0;
  let emptyContentCount = 0;
  let noContextCount = 0;

  for (const raw of items) {
    const item = asRecord(raw);
    if (!item || readRunId(item) !== runId) continue;
    const mvp = asRecord(item.mvp);
    const gen = asRecord(mvp?.generation);
    const ret = asRecord(mvp?.retrieval);
    const analysis = asRecord(mvp?.analysis);
    const op = asRecord(mvp?.operational);
    const outcome = typeof op?.outcome === "string" ? op.outcome : typeof item.status === "string" ? item.status : "";
    const failureCode = typeof op?.failureCode === "string" ? op.failureCode : "";
    const skipCode = typeof op?.skipReasonCode === "string" ? op.skipReasonCode : "";

    if (outcome === "FAILED") errorCount += 1;
    if (isTimeoutOutcome(outcome, failureCode, skipCode)) timeoutCount += 1;
    if (outcome === "EXECUTED" && isEmptyContent(item)) emptyContentCount += 1;
    if (isNoContext(item)) noContextCount += 1;

    const r1 = numberOrNull(ret?.recallAt1 ?? analysis?.recallAt1);
    const r3 = numberOrNull(ret?.recallAt3 ?? analysis?.recallAt3);
    const r5 = numberOrNull(ret?.recallAt5 ?? analysis?.recallAt5);
    const mrr = numberOrNull(ret?.mrr ?? analysis?.mrr);
    const ndcg = numberOrNull(analysis?.ndcgAt5 ?? ret?.ndcgAt5);
    if (r1 != null) recall1.push(r1);
    if (r3 != null) recall3.push(r3);
    if (r5 != null) recall5.push(r5);
    if (mrr != null) mrrs.push(mrr);
    if (ndcg != null) ndcgs.push(ndcg);

    const corr = numberOrNull(gen?.correctness);
    const faith = numberOrNull(gen?.faithfulness);
    const hall = numberOrNull(gen?.hallucinationRate);
    const sem = numberOrNull(gen?.semanticScore ?? gen?.llmJudgeScore);
    const cea = numberOrNull(gen?.containsExpectedAnswer);
    if (corr != null) correctness.push(corr);
    if (faith != null) faithfulness.push(faith);
    if (hall != null) hallucination.push(hall);
    if (sem != null) semantic.push(sem);
    if (cea != null) containsExpected.push(cea);

    const latency = numberOrNull(op?.latencyMs);
    if (latency != null) latencies.push(latency);
  }

  return {
    meanRecallAt1: mean(recall1),
    meanRecallAt3: mean(recall3),
    meanRecallAt5: mean(recall5),
    meanMrr: mean(mrrs),
    meanNdcgAt5: mean(ndcgs),
    meanCorrectness: mean(correctness),
    meanFaithfulness: mean(faithfulness),
    meanHallucinationRate: mean(hallucination),
    meanSemanticScore: mean(semantic),
    containsExpectedAnswerRate: mean(containsExpected),
    meanLatencyMs: mean(latencies),
    p95LatencyMs: p95(latencies),
    errorCount,
    timeoutCount,
    emptyContentCount,
    noContextCount,
  };
}

export function mergeComparisonRowWithEnrichment(
  row: ComparisonRow,
  enrichment: ComparisonMetricEnrichment,
): ComparisonRow {
  return {
    ...row,
    meanRecallAt1: row.meanRecallAt1 ?? enrichment.meanRecallAt1 ?? null,
    meanRecallAt3: row.meanRecallAt3 ?? enrichment.meanRecallAt3 ?? null,
    meanRecallAt5: row.meanRecallAt5 ?? enrichment.meanRecallAt5 ?? null,
    meanMrr: row.meanMrr ?? enrichment.meanMrr ?? null,
    meanNdcgAt5: row.meanNdcgAt5 ?? enrichment.meanNdcgAt5 ?? null,
    meanExactMatch: row.meanExactMatch ?? enrichment.meanCorrectness ?? null,
    meanSemanticScore: row.meanSemanticScore ?? enrichment.meanSemanticScore ?? null,
    meanCorrectness: enrichment.meanCorrectness ?? null,
    meanFaithfulness: enrichment.meanFaithfulness ?? null,
    meanHallucinationRate: enrichment.meanHallucinationRate ?? null,
    containsExpectedAnswerRate: enrichment.containsExpectedAnswerRate ?? null,
    meanLatencyMs: row.meanLatencyMs ?? enrichment.meanLatencyMs ?? null,
    p95LatencyMs: enrichment.p95LatencyMs ?? null,
    errorCount: enrichment.errorCount ?? 0,
    timeoutCount: enrichment.timeoutCount ?? 0,
    emptyContentCount: enrichment.emptyContentCount ?? 0,
    noContextCount: enrichment.noContextCount ?? 0,
  };
}

export function enrichComparisonRowsFromItems(
  rows: ComparisonRow[],
  items: unknown[],
): ComparisonRow[] {
  return rows.map((row) => {
    const runId = typeof row.runId === "string" ? row.runId.trim() : "";
    if (!runId) return row;
    const enrichment = aggregateRunMetricsFromItems(items, runId);
    return mergeComparisonRowWithEnrichment(row, enrichment);
  });
}

export function formatRatioPercent(numerator: number, denominator: number): string {
  if (denominator <= 0) return "-";
  return `${((numerator / denominator) * 100).toFixed(1)}%`;
}

export function formatMetricNumber(value: unknown, digits = 3): string {
  if (value == null || value === "NOT_AVAILABLE") return "-";
  if (typeof value === "number" && Number.isFinite(value)) return value.toFixed(digits);
  const text = String(value).trim();
  return text.length > 0 ? text : "-";
}

export function formatLatencyMs(value: unknown): string {
  if (value == null || value === "NOT_AVAILABLE") return "-";
  if (typeof value === "number" && Number.isFinite(value)) return `${Math.round(value)}`;
  return "-";
}
