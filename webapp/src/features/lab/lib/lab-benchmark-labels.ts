import { formatBenchmarkKindLabel } from "@/lib/product-copy";

/** Normalized display labels for LAB benchmark results, exports, and comparison tables. */

export { formatBenchmarkKindLabel };

export const MISSING_METADATA_KEY = "MISSING_METADATA";
export const LEGACY_UNKNOWN_KEY = "_UNKNOWN";
export const NOT_AVAILABLE = "NOT_AVAILABLE";

export type ComparisonAxis = "LLM_MODEL" | "EMBEDDING_MODEL" | "PRESET_CODE" | "UNKNOWN";

export type BenchmarkKindHint =
  | "LLM_JUDGE_QA"
  | "EMBEDDING_RETRIEVAL"
  | "RAG_PRESET_END_TO_END"
  | string
  | null
  | undefined;

export function normalizeMetadataKey(raw: string | null | undefined): string {
  const t = (raw ?? "").trim();
  if (!t || t === LEGACY_UNKNOWN_KEY || t === MISSING_METADATA_KEY) {
    return MISSING_METADATA_KEY;
  }
  return t;
}

export function isMissingMetadata(raw: string | null | undefined): boolean {
  return normalizeMetadataKey(raw) === MISSING_METADATA_KEY;
}

export function comparisonAxisForKind(kind: BenchmarkKindHint): ComparisonAxis {
  switch (kind) {
    case "LLM_JUDGE_QA":
      return "LLM_MODEL";
    case "EMBEDDING_RETRIEVAL":
      return "EMBEDDING_MODEL";
    case "RAG_PRESET_END_TO_END":
      return "PRESET_CODE";
    default:
      return "UNKNOWN";
  }
}

export function isExtensionPreset(presetCode: string | null | undefined): boolean {
  const code = normalizeMetadataKey(presetCode);
  return code === "P13" || code === "P14";
}

export function formatPresetDisplay(
  presetCode: string | null | undefined,
  presetLabel: string | null | undefined,
): string {
  const code = normalizeMetadataKey(presetCode);
  if (code === MISSING_METADATA_KEY) {
    return "";
  }
  const label = (presetLabel ?? "").trim();
  if (label && label !== code) {
    return `${code} — ${label}`;
  }
  return code;
}

export function formatGroupLabel(
  groupKey: string | null | undefined,
  groupValue: string | null | undefined,
  t: (key: string) => string,
): string {
  const normalized = normalizeMetadataKey(groupValue);
  if (normalized === MISSING_METADATA_KEY) {
    return t("benchmarkLabelMissingMetadata");
  }
  if (groupKey === "presetCode") {
    return formatPresetDisplay(normalized, null) || t("benchmarkLabelMissingMetadata");
  }
  return normalized;
}

export function formatOutcomeLabel(outcome: string, t: (key: string) => string): string {
  const known = new Set(["EXECUTED", "FAILED", "SKIPPED", "NOT_SUPPORTED"]);
  if (known.has(outcome)) {
    const key = `benchmarkOutcomeLabel.${outcome}`;
    const out = t(key);
    return out === key ? outcome : out;
  }
  const unknownKey = "benchmarkOutcomeLabel.unknown";
  const unknown = t(unknownKey);
  return unknown === unknownKey ? outcome : unknown;
}

export function isNotAvailable(value: unknown): boolean {
  return value === NOT_AVAILABLE || value === "NOT_AVAILABLE";
}

export function metricUnavailableReasonKey(
  metricKey: string,
  benchmarkKind: BenchmarkKindHint,
  outcome: string,
): string {
  const retrievalMetrics = new Set(["recallAt1", "recallAt3", "recallAt5", "mrr", "retrievedCount", "goldFound"]);
  const judgeMetrics = new Set(["llmJudgeScore", "semanticScore", "faithfulness", "sourceSupport", "hallucinationRate"]);

  if (retrievalMetrics.has(metricKey)) {
    if (benchmarkKind === "LLM_JUDGE_QA") {
      return "benchmarkMetricReasonNoRetrieval";
    }
    if (outcome === "FAILED" || outcome === "SKIPPED") {
      return "benchmarkMetricReasonRunIncomplete";
    }
    return "benchmarkMetricReasonRetrievalNa";
  }
  if (judgeMetrics.has(metricKey)) {
    return "benchmarkMetricReasonNoJudge";
  }
  if (outcome === "FAILED") {
    return "benchmarkMetricReasonRunFailed";
  }
  if (outcome === "SKIPPED" || outcome === "NOT_SUPPORTED") {
    return "benchmarkMetricReasonSkipped";
  }
  return "benchmarkMetricReasonGeneric";
}

export function formatMetricCell(
  value: number | string | null | undefined,
  metricKey: string,
  benchmarkKind: BenchmarkKindHint,
  outcome: string,
  t: (key: string) => string,
): { display: string; title: string | undefined } {
  if (value == null || isNotAvailable(value)) {
    const reasonKey = metricUnavailableReasonKey(metricKey, benchmarkKind, outcome);
    return { display: "—", title: t(reasonKey) };
  }
  if (typeof value === "number" && Number.isFinite(value)) {
    return { display: value.toFixed(3), title: undefined };
  }
  const text = String(value).trim();
  if (!text || isNotAvailable(text)) {
    const reasonKey = metricUnavailableReasonKey(metricKey, benchmarkKind, outcome);
    return { display: "—", title: t(reasonKey) };
  }
  return { display: text, title: undefined };
}

export type ComparisonRow = {
  groupKey?: string;
  groupValue?: string;
  axisValue?: string;
  comparisonLabel?: string;
  comparisonAxis?: string;
  presetKey?: string;
  presetLabel?: string;
  modelLabel?: string;
  runId?: string;
  llmModelId?: string;
  embeddingModelId?: string;
  totalItems?: number;
  executed?: number;
  notSupported?: number;
  failed?: number;
  skipped?: number;
  meanExactMatch?: number | null;
  meanSemanticScore?: number | null;
  meanRecallAt1?: number | null;
  meanLatencyMs?: number | null;
};

export function isPresetComparisonAxis(axis: string | null | undefined): boolean {
  const a = (axis ?? "").trim().toUpperCase();
  return a === "PRESET_CODE" || a === "PRESET" || a === "RAG_PRESET";
}

export function resolvePresetKeyFromComparisonRow(row: ComparisonRow): string {
  const r = row as ComparisonRow & Record<string, unknown>;
  const fromKey = typeof r.presetKey === "string" ? r.presetKey.trim() : "";
  if (fromKey && !isMissingMetadata(fromKey)) {
    return fromKey;
  }
  const axisValue =
    typeof r.axisValue === "string"
      ? r.axisValue.trim()
      : typeof r.groupValue === "string"
        ? r.groupValue.trim()
        : "";
  if (axisValue && !isMissingMetadata(axisValue)) {
    return axisValue;
  }
  const presetLabel = typeof r.presetLabel === "string" ? r.presetLabel.trim() : "";
  const presetMatch = presetLabel.match(/^(P\d+)\b/i);
  return presetMatch?.[1]?.toUpperCase() ?? "";
}

export function resolveComparisonRowLabel(
  row: ComparisonRow,
  comparisonAxis: string | null | undefined,
): string {
  const r = row as ComparisonRow & Record<string, unknown>;
  const stored =
    typeof r.comparisonLabel === "string" && r.comparisonLabel.trim() ? r.comparisonLabel.trim() : "";
  if (stored) {
    return stored;
  }
  const presetLabel = typeof r.presetLabel === "string" ? r.presetLabel.trim() : "";
  const presetKey = resolvePresetKeyFromComparisonRow(row);
  if (isPresetComparisonAxis(comparisonAxis)) {
    if (presetLabel) {
      return presetLabel.includes("—") ? presetLabel : formatPresetDisplay(presetKey, presetLabel);
    }
    if (presetKey) {
      return presetKey;
    }
  }
  const modelLabel = typeof r.modelLabel === "string" ? r.modelLabel.trim() : "";
  if (modelLabel) {
    return modelLabel;
  }
  const axisValue =
    typeof r.axisValue === "string"
      ? r.axisValue.trim()
      : typeof r.groupValue === "string"
        ? r.groupValue.trim()
        : "";
  return axisValue;
}

export function aggregateComparisonOutcomeCounts(rows: ComparisonRow[]): Record<string, number> {
  const totals: Record<string, number> = {};
  for (const row of rows) {
    const add = (key: string, value: unknown) => {
      if (typeof value === "number" && Number.isFinite(value) && value > 0) {
        totals[key] = (totals[key] ?? 0) + value;
      }
    };
    add("EXECUTED", row.executed);
    add("FAILED", row.failed);
    add("SKIPPED", row.skipped);
    add("NOT_SUPPORTED", row.notSupported);
  }
  return totals;
}

export function parseComparisonRows(payload: unknown): ComparisonRow[] {
  if (!payload || typeof payload !== "object") {
    return [];
  }
  const root = payload as Record<string, unknown>;
  const comparisonAxis = typeof root.comparisonAxis === "string" ? root.comparisonAxis : "";
  const rows = root.rows;
  if (!Array.isArray(rows)) {
    return [];
  }
  return rows
    .filter((row): row is ComparisonRow => row != null && typeof row === "object")
    .map((row) => {
      const r = row as ComparisonRow & Record<string, unknown>;
      const comparisonLabel = resolveComparisonRowLabel(r, comparisonAxis);
      const presetKey = resolvePresetKeyFromComparisonRow(r);
      return { ...r, comparisonLabel, presetKey: presetKey || undefined };
    });
}

export function sortComparisonRows(rows: ComparisonRow[]): ComparisonRow[] {
  return [...rows].sort((a, b) => {
    const av = typeof a.meanExactMatch === "number" ? a.meanExactMatch : -1;
    const bv = typeof b.meanExactMatch === "number" ? b.meanExactMatch : -1;
    return bv - av;
  });
}

export function shouldShowPresetTrend(benchmarkKind: BenchmarkKindHint, plottableCount: number): boolean {
  return benchmarkKind === "RAG_PRESET_END_TO_END" && plottableCount > 0;
}

export function shouldShowTrendEmptyState(benchmarkKind: BenchmarkKindHint, hasItems: boolean, plottableCount: number): boolean {
  return benchmarkKind === "RAG_PRESET_END_TO_END" && hasItems && plottableCount === 0;
}
