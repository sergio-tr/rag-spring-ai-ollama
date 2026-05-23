"use client";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  downloadCampaignExport,
  downloadCampaignMvpItemsJson,
  downloadMvpExport,
  fetchCampaignComparison,
  fetchLabCampaignRuns,
  fetchLabEvaluationRun,
  fetchMvpItemsBundle,
  fetchMvpRollupsJson,
} from "@/features/lab/lib/lab-benchmark-results-api";
import {
  formatGroupLabel,
  formatMetricCell,
  formatOutcomeLabel,
  formatPresetDisplay,
  isExtensionPreset,
  isMissingMetadata,
  normalizeMetadataKey,
  parseComparisonRows,
  shouldShowPresetTrend,
  shouldShowTrendEmptyState,
  sortComparisonRows,
  type ComparisonRow,
} from "@/features/lab/lib/lab-benchmark-labels";
import {
  readGlobalOutcomeCounts,
  readMvpItems,
  readOnExecutedSummary,
} from "@/features/lab/lib/lab-benchmark-mvp-utils";
import { getSafeApiErrorMessage } from "@/lib/api-client";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";

const OUTCOME_ORDER = ["EXECUTED", "FAILED", "SKIPPED", "NOT_SUPPORTED"] as const;
const PRIMARY_OUTCOME_KEYS = new Set<string>(OUTCOME_ORDER);
const FAILED_SKIPPED_OUTCOMES = new Set(["FAILED", "SKIPPED", "NOT_SUPPORTED"]);

type ResultTableRow = {
  id: string;
  question: string;
  outcome: string;
  note: string;
  presetCode: string;
  presetLabel: string;
  modelId: string;
  correctness: number | null;
  llmJudgeScore: number | null;
  hallucinationRate: number | null;
  faithfulness: number | null;
  sourceSupport: number | null;
  dateCorrectness: number | null;
};

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null;
}

function numberOrNull(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function sortedUnique(values: string[]): string[] {
  return Array.from(new Set(values.filter((value) => value.trim().length > 0))).sort((a, b) => a.localeCompare(b));
}

function displayModelId(modelId: string, t: (key: string) => string): string {
  if (!modelId || modelId === "—" || isMissingMetadata(modelId)) {
    return t("benchmarkLabelMissingMetadata");
  }
  return modelId;
}

function toResultTableRow(row: unknown, idx: number, t: (key: string) => string): ResultTableRow {
  const item = asRecord(row);
  const mvp = asRecord(item?.mvp);
  const generation = asRecord(mvp?.generation);
  const op = asRecord(mvp?.operational);
  const outcome = typeof op?.outcome === "string" && op.outcome ? op.outcome : "—";
  const unsupportedReason =
    typeof op?.unsupportedReason === "string" && op.unsupportedReason.trim() ? op.unsupportedReason.trim() : "";
  const skipReason = typeof op?.skipReason === "string" && op.skipReason.trim() ? op.skipReason.trim() : "";
  const rawPreset = typeof op?.presetCode === "string" ? op.presetCode.trim() : "";
  const presetCode = rawPreset && !isMissingMetadata(rawPreset) ? rawPreset : "—";
  const rawModel = typeof op?.modelId === "string" ? op.modelId.trim() : "";
  const modelId = rawModel && !isMissingMetadata(rawModel) ? rawModel : "—";
  const presetLabelRaw = typeof item?.presetLabel === "string" ? item.presetLabel : "";
  const mp = asRecord(item?.metricsPayload);
  const presetLabelFromPayload = typeof mp?.presetLabel === "string" ? mp.presetLabel : presetLabelRaw;
  const presetLabel = presetCode !== "—" ? formatPresetDisplay(presetCode, presetLabelFromPayload) : "—";
  const question = typeof item?.questionText === "string" ? item.questionText : "";
  const note =
    isExtensionPreset(presetCode)
      ? t("benchmarkNoteExtension")
      : outcome === "NOT_SUPPORTED"
        ? unsupportedReason || formatOutcomeLabel("NOT_SUPPORTED", t)
        : outcome === "SKIPPED"
          ? skipReason || formatOutcomeLabel("SKIPPED", t)
          : outcome === "FAILED"
            ? t("benchmarkNoteSeeExport")
            : "—";
  return {
    id: typeof item?.id === "string" && item.id ? item.id : `row-${idx}`,
    question,
    outcome,
    note,
    presetCode,
    presetLabel,
    modelId,
    correctness: numberOrNull(generation?.correctness),
    llmJudgeScore: numberOrNull(generation?.llmJudgeScore),
    hallucinationRate: numberOrNull(generation?.hallucinationRate),
    faithfulness: numberOrNull(generation?.faithfulness),
    sourceSupport: numberOrNull(generation?.sourceSupport),
    dateCorrectness: numberOrNull(generation?.dateCorrectness),
  };
}

function trendRows(rows: ResultTableRow[]): Array<{ presetCode: string; score: number | null; extension: boolean }> {
  const grouped = new Map<string, number[]>();
  for (const row of rows) {
    if (!row.presetCode || row.presetCode === "—") continue;
    if (row.correctness == null) {
      grouped.set(row.presetCode, grouped.get(row.presetCode) ?? []);
      continue;
    }
    grouped.set(row.presetCode, [...(grouped.get(row.presetCode) ?? []), row.correctness]);
  }
  return Array.from(grouped.entries())
    .sort(([a], [b]) => a.localeCompare(b, undefined, { numeric: true }))
    .map(([presetCode, values]) => ({
      presetCode,
      score: values.length > 0 ? values.reduce((sum, value) => sum + value, 0) / values.length : null,
      extension: isExtensionPreset(presetCode),
    }));
}

function sortByCorrectnessDesc(rows: ResultTableRow[]): ResultTableRow[] {
  return [...rows].sort((a, b) => {
    const av = a.correctness ?? -1;
    const bv = b.correctness ?? -1;
    return bv - av;
  });
}

function comparisonAxisLabel(payload: Record<string, unknown>, t: (key: string, values?: Record<string, string>) => string): string {
  const label = typeof payload.comparisonAxisLabel === "string" ? payload.comparisonAxisLabel : "";
  if (label) {
    return label;
  }
  const axis = typeof payload.comparisonAxis === "string" ? payload.comparisonAxis : "";
  return axis || t("benchmarkColComparisonLabel");
}

function formatComparisonMetric(value: unknown): string {
  if (value == null || value === "NOT_AVAILABLE") {
    return "—";
  }
  if (typeof value === "number" && Number.isFinite(value)) {
    return value.toFixed(3);
  }
  const text = String(value).trim();
  return text.length > 0 ? text : "—";
}

function MetricCell({
  value,
  metricKey,
  benchmarkKind,
  outcome,
  t,
}: {
  value: number | null;
  metricKey: string;
  benchmarkKind: string | null | undefined;
  outcome: string;
  t: (key: string) => string;
}) {
  const cell = formatMetricCell(value, metricKey, benchmarkKind, outcome, t);
  return (
    <span className="font-mono" title={cell.title}>
      {cell.display}
    </span>
  );
}

export type LabBenchmarkResultsPanelProps = {
  evaluationRunId: string | null;
  campaignId?: string | null;
  /** When false, the panel does not fetch (job still running or failed). */
  loadEnabled: boolean;
};

/**
 * After a successful benchmark async task, loads run metadata, MVP rollups, and per-item MVP rows for UX (not the removed CSV).
 */
export function LabBenchmarkResultsPanel({ evaluationRunId, campaignId, loadEnabled }: LabBenchmarkResultsPanelProps) {
  const t = useTranslations("Lab");
  const runId = evaluationRunId?.trim() ?? "";
  const enabled = loadEnabled && runId.length > 0;
  const campId = campaignId?.trim() ?? "";
  const [modelFilter, setModelFilter] = useState("ALL");
  const [presetFilter, setPresetFilter] = useState("ALL");

  const query = useQuery({
    queryKey: ["lab", "benchmark-mvp-results", runId, campId],
    enabled,
    queryFn: async () => {
      const [run, rollups, itemsBundle] = await Promise.all([
        fetchLabEvaluationRun(runId),
        fetchMvpRollupsJson(runId),
        fetchMvpItemsBundle(runId),
      ]);
      const campaignRuns = campId ? await fetchLabCampaignRuns(campId) : null;
      const campaignComparison = campId ? await fetchCampaignComparison(campId).catch(() => null) : null;
      return { run, rollups, itemsBundle, campaignRuns, campaignComparison };
    },
  });

  const benchmarkKind = query.data?.run.benchmarkKind ?? null;
  const campaignComparison = query.data?.campaignComparison;

  const comparisonRows = useMemo(() => {
    if (!campaignComparison) {
      return [] as ComparisonRow[];
    }
    return sortComparisonRows(parseComparisonRows(campaignComparison));
  }, [campaignComparison]);

  if (!enabled) {
    return null;
  }

  if (query.isPending) {
    return (
      <div className="text-muted-foreground text-sm" data-testid="lab-benchmark-results-loading">
        {t("benchmarkResultsLoading")}
      </div>
    );
  }

  if (query.isError) {
    return (
      <p className="text-destructive text-sm" role="alert" data-testid="lab-benchmark-results-error">
        {t("benchmarkResultsError", { message: getSafeApiErrorMessage(query.error) })}
      </p>
    );
  }

  const payload = query.data;
  if (!payload) {
    return null;
  }

  const occ = readGlobalOutcomeCounts(payload.rollups);
  const macroExecuted = readOnExecutedSummary(payload.rollups.globalMacro);
  const mvpRows = readMvpItems(payload.itemsBundle);
  const tableRows = mvpRows.map((row, idx) => toResultTableRow(row, idx, t));
  const modelOptions = sortedUnique(tableRows.map((row) => row.modelId).filter((id) => id !== "—"));
  const presetOptions = sortedUnique(tableRows.map((row) => row.presetCode).filter((code) => code !== "—"));
  const filteredRows = sortByCorrectnessDesc(
    tableRows.filter(
      (row) =>
        (modelFilter === "ALL" || row.modelId === modelFilter) &&
        (presetFilter === "ALL" || row.presetCode === presetFilter),
    ),
  );
  const executedRows = filteredRows.filter((row) => row.outcome === "EXECUTED");
  const failedSkippedRows = filteredRows.filter((row) => FAILED_SKIPPED_OUTCOMES.has(row.outcome));
  const trend = trendRows(filteredRows);
  const plottableTrend = trend.filter((point) => point.score != null);
  const showTrendChart = shouldShowPresetTrend(benchmarkKind, plottableTrend.length);
  const showTrendEmpty = shouldShowTrendEmptyState(benchmarkKind, mvpRows.length > 0, plottableTrend.length);
  const hasExtensionPresets = tableRows.some((row) => isExtensionPreset(row.presetCode));
  const uniqueQuestionIds = new Set<string>();
  const uniquePresetCodes = new Set<string>();
  for (const row of mvpRows) {
    if (!row || typeof row !== "object") continue;
    const mvp = (row as Record<string, unknown>).mvp;
    if (!mvp || typeof mvp !== "object") continue;
    const m = mvp as Record<string, unknown>;
    const dq = m.datasetQuestionId;
    if (typeof dq === "string" && dq.trim()) uniqueQuestionIds.add(dq.trim());
    const op = m.operational;
    if (op && typeof op === "object") {
      const pc = (op as Record<string, unknown>).presetCode;
      const normalized = normalizeMetadataKey(typeof pc === "string" ? pc : "");
      if (normalized !== "MISSING_METADATA") uniquePresetCodes.add(normalized);
    }
  }

  const campaignComparisonRecord =
    payload.campaignComparison && typeof payload.campaignComparison === "object"
      ? (payload.campaignComparison as Record<string, unknown>)
      : null;

  return (
    <div className="space-y-4 rounded-md border bg-muted/20 p-4" data-testid="lab-benchmark-results-panel">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <Label className="text-base">{t("benchmarkResultsTitle")}</Label>
          <p className="text-muted-foreground text-xs">
            {t("benchmarkResultsRunLine", {
              id: payload.run.id.slice(0, 8),
              status: payload.run.status,
              kind: payload.run.benchmarkKind ?? "—",
            })}
          </p>
          {macroExecuted && macroExecuted.n > 0 ? (
            <p className="text-muted-foreground text-xs">
              {t("benchmarkResultsExecutedSummary", {
                n: macroExecuted.n,
                exact:
                  macroExecuted.meanNormalizedExactMatch != null
                    ? macroExecuted.meanNormalizedExactMatch.toFixed(3)
                    : "—",
              })}
            </p>
          ) : null}
          <p className="text-muted-foreground text-xs" data-testid="lab-benchmark-results-counts">
            {t("benchmarkResultsCountsLine", {
              items: mvpRows.length,
              questions: uniqueQuestionIds.size,
              presets: uniquePresetCodes.size,
            })}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {campId ? (
            <>
              <Button
                type="button"
                variant="outline"
                size="sm"
                data-testid="lab-export-campaign-items-json"
                onClick={() => void downloadCampaignMvpItemsJson(campId)}
              >
                {t("benchmarkExportCampaignItemsJson")}
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                data-testid="lab-export-campaign-items-csv"
                onClick={() => void downloadCampaignExport(campId, "items.csv")}
              >
                {t("benchmarkExportCampaignItemsCsv")}
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                data-testid="lab-export-campaign-summary-csv"
                onClick={() => void downloadCampaignExport(campId, "summary.csv")}
              >
                {t("benchmarkExportCampaignSummaryCsv")}
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                data-testid="lab-export-campaign-bundle-json"
                onClick={() => void downloadCampaignExport(campId, "bundle.json")}
              >
                {t("benchmarkExportCampaignBundleJson")}
              </Button>
            </>
          ) : null}
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid="lab-export-mvp-csv"
            onClick={() => void downloadMvpExport(payload.run.id, "items.csv")}
          >
            {t("benchmarkExportMvpCsv")}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid="lab-export-mvp-items-json"
            onClick={() => void downloadMvpExport(payload.run.id, "items.json")}
          >
            {t("benchmarkExportMvpItemsJson")}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid="lab-export-mvp-rollups-json"
            onClick={() => void downloadMvpExport(payload.run.id, "rollups.json")}
          >
            {t("benchmarkExportMvpRollupsJson")}
          </Button>
        </div>
      </div>

      {campId && payload.campaignRuns && payload.campaignRuns.length > 0 ? (
        <div className="space-y-2" data-testid="lab-campaign-runs-panel">
          <span className="text-muted-foreground text-xs font-medium">{t("benchmarkCampaignRunsTitle")}</span>
          <div className="max-h-40 overflow-auto rounded-md border bg-background/40 p-2 text-xs">
            <ul className="space-y-1">
              {payload.campaignRuns.slice(0, 40).map((r, idx) => {
                const row = r as Record<string, unknown>;
                const rid = typeof row.runId === "string" ? row.runId : `row-${idx}`;
                const model =
                  typeof row.modelLabel === "string" && row.modelLabel
                    ? row.modelLabel
                    : typeof row.llmModelId === "string"
                      ? displayModelId(row.llmModelId, t)
                      : typeof row.embeddingModelId === "string"
                        ? displayModelId(row.embeddingModelId, t)
                        : "—";
                const preset =
                  typeof row.presetLabel === "string" && row.presetLabel
                    ? row.presetLabel
                    : typeof row.presetCode === "string"
                      ? row.presetCode
                      : "";
                const axisLabel = preset || model;
                const status = typeof row.status === "string" ? row.status : "—";
                return (
                  <li
                    key={rid}
                    className="flex flex-wrap items-center justify-between gap-2 border-border border-b py-1 last:border-b-0"
                    data-testid={`lab-campaign-run-row-${idx}`}
                  >
                    <span className="font-mono">{rid.slice(0, 8)}</span>
                    <span className="truncate">{axisLabel}</span>
                    <span className="text-muted-foreground font-mono">{status}</span>
                  </li>
                );
              })}
            </ul>
            {payload.campaignRuns.length > 40 ? (
              <p className="text-muted-foreground mt-2">{t("benchmarkCampaignRunsTruncated", { n: payload.campaignRuns.length })}</p>
            ) : null}
          </div>
          <p className="text-muted-foreground text-[11px]">{t("benchmarkCampaignRunsHint")}</p>
        </div>
      ) : null}

      {campId && campaignComparisonRecord ? (
        comparisonRows.length >= 2 ? (
        <div className="space-y-2" data-testid="lab-campaign-comparison-panel">
          <span className="text-muted-foreground text-xs font-medium">{t("benchmarkCampaignComparisonTitle")}</span>
          {campaignComparisonRecord ? (
            <p className="text-muted-foreground text-[11px]">
              {t("benchmarkCampaignComparisonAxis", {
                axis: comparisonAxisLabel(campaignComparisonRecord, t),
              })}
            </p>
          ) : null}
          <div className="max-h-56 overflow-auto rounded-md border">
            <table className="w-full text-left text-xs">
              <thead className="bg-muted/50 sticky top-0">
                <tr>
                  <th className="p-2 font-medium">{t("benchmarkColComparisonLabel")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColTotal")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColExecuted")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColNotSupported")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColFailed")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColSkipped")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColExact")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColSemantic")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColRecall")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColLatency")}</th>
                </tr>
              </thead>
              <tbody>
                {comparisonRows.slice(0, 50).map((r, idx) => {
                  const label =
                    r.comparisonLabel ??
                    formatGroupLabel(r.groupKey ?? "", r.groupValue ?? "", t);
                  return (
                    <tr key={`${label}-${idx}`} className="border-border border-t" data-testid={`lab-comparison-row-${idx}`}>
                      <td className="p-2">{label}</td>
                      <td className="p-2">{String(r.totalItems ?? "—")}</td>
                      <td className="p-2">{String(r.executed ?? "—")}</td>
                      <td className="p-2">{String(r.notSupported ?? "—")}</td>
                      <td className="p-2">{String(r.failed ?? "—")}</td>
                      <td className="p-2">{String(r.skipped ?? "—")}</td>
                      <td className="p-2 font-mono">{formatComparisonMetric(r.meanExactMatch)}</td>
                      <td className="p-2 font-mono">{formatComparisonMetric(r.meanSemanticScore)}</td>
                      <td className="p-2 font-mono">{formatComparisonMetric(r.meanRecallAt1)}</td>
                      <td className="p-2 font-mono">{formatComparisonMetric(r.meanLatencyMs)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <p className="text-muted-foreground text-[11px]">{t("benchmarkCampaignComparisonHint")}</p>
        </div>
        ) : (
          <output className="text-muted-foreground block text-xs" data-testid="lab-campaign-comparison-empty">
            {t("benchmarkCampaignComparisonEmpty")}
          </output>
        )
      ) : null}

      <div className="space-y-2">
        <span className="text-muted-foreground text-xs font-medium">{t("benchmarkOutcomesTitle")}</span>
        <div className="flex flex-wrap gap-2">
          {OUTCOME_ORDER.map((key) => {
            const n = occ[key] ?? 0;
            return (
              <span
                key={key}
                className="bg-background rounded-md border px-2 py-1 font-mono text-xs"
                data-testid={`lab-outcome-${key}`}
              >
                {t(`benchmarkOutcome.${key}`, { count: n })}
              </span>
            );
          })}
          {Object.entries(occ)
            .filter(([k]) => !PRIMARY_OUTCOME_KEYS.has(k))
            .map(([k, n]) => (
              <span key={k} className="bg-background rounded-md border px-2 py-1 font-mono text-xs">
                {k}: {n}
              </span>
            ))}
        </div>
      </div>

      {hasExtensionPresets ? (
        <p className="text-muted-foreground text-[11px]" data-testid="lab-extension-legend">
          {t("benchmarkExtensionLegend")}
        </p>
      ) : null}

      <div className="grid gap-3 md:grid-cols-2" data-testid="lab-results-filters">
        <label className="space-y-1 text-xs">
          <span className="text-muted-foreground font-medium">{t("benchmarkFilterModel")}</span>
          <select
            className="bg-background w-full rounded-md border px-2 py-1"
            data-testid="lab-results-filter-model"
            value={modelFilter}
            onChange={(event) => setModelFilter(event.target.value)}
          >
            <option value="ALL">{t("benchmarkFilterAll")}</option>
            {modelOptions.map((model) => (
              <option key={model} value={model}>
                {displayModelId(model, t)}
              </option>
            ))}
          </select>
        </label>
        <label className="space-y-1 text-xs">
          <span className="text-muted-foreground font-medium">{t("benchmarkFilterPreset")}</span>
          <select
            className="bg-background w-full rounded-md border px-2 py-1"
            data-testid="lab-results-filter-preset"
            value={presetFilter}
            onChange={(event) => setPresetFilter(event.target.value)}
          >
            <option value="ALL">{t("benchmarkFilterAll")}</option>
            {presetOptions.map((preset) => (
              <option key={preset} value={preset}>
                {preset}
              </option>
            ))}
          </select>
        </label>
      </div>

      {showTrendChart ? (
        <div className="space-y-2" data-testid="lab-benchmark-trend-graph">
          <span className="text-muted-foreground text-xs font-medium">{t("benchmarkTrendTitle")}</span>
          <div className="flex min-h-28 items-end gap-2 rounded-md border bg-background/40 p-3">
            {plottableTrend.slice(0, 15).map((point) => {
              const height = point.score == null ? 6 : Math.max(8, Math.round(point.score * 88));
              return (
                <div key={point.presetCode} className="flex min-w-10 flex-1 flex-col items-center gap-1">
                  <div
                    className={point.extension ? "w-full rounded-t bg-amber-500/70" : "w-full rounded-t bg-primary/70"}
                    style={{ height }}
                    title={`${point.presetCode}: ${point.score?.toFixed(3) ?? "—"}`}
                  />
                  <span className="font-mono text-[10px]">{point.presetCode}</span>
                  {point.extension ? <span className="text-muted-foreground text-[9px]">ext</span> : null}
                </div>
              );
            })}
          </div>
          <p className="text-muted-foreground text-[11px]">{t("benchmarkTrendHint")}</p>
        </div>
      ) : null}

      {showTrendEmpty ? (
        <p className="text-muted-foreground text-xs" data-testid="lab-benchmark-trend-empty">
          {t("benchmarkTrendEmpty")}
        </p>
      ) : null}

      <div className="space-y-2">
        <span className="text-muted-foreground text-xs font-medium">{t("benchmarkPerItemTitle")}</span>
        <div className="max-h-56 overflow-auto rounded-md border">
          <table className="w-full text-left text-xs">
            <thead className="bg-muted/50 sticky top-0">
              <tr>
                <th className="p-2 font-medium">{t("benchmarkColItem")}</th>
                <th className="p-2 font-medium">{t("benchmarkColPreset")}</th>
                <th className="p-2 font-medium">{t("benchmarkColModel")}</th>
                <th className="p-2 font-medium">{t("benchmarkColOutcome")}</th>
                <th className="p-2 font-medium">{t("benchmarkColCorrectness")}</th>
                <th className="p-2 font-medium">{t("benchmarkColJudge")}</th>
                <th className="p-2 font-medium">{t("benchmarkColHallucination")}</th>
                <th className="p-2 font-medium">{t("benchmarkColDate")}</th>
                <th className="p-2 font-medium">{t("benchmarkColNote")}</th>
              </tr>
            </thead>
            <tbody>
              {executedRows.slice(0, 80).map((row) => {
                const snippet = row.question.length > 96 ? `${row.question.slice(0, 96)}…` : row.question;
                return (
                  <tr key={row.id} className="border-t border-border" data-testid={`lab-item-row-${row.id}`}>
                    <td className="p-2 align-top">{snippet || "—"}</td>
                    <td className="p-2 align-top">{row.presetLabel !== "—" ? row.presetLabel : "—"}</td>
                    <td className="p-2 align-top">{displayModelId(row.modelId, t)}</td>
                    <td className="p-2 align-top">{formatOutcomeLabel(row.outcome, t)}</td>
                    <td className="p-2 align-top">
                      <MetricCell
                        value={row.correctness}
                        metricKey="correctness"
                        benchmarkKind={benchmarkKind}
                        outcome={row.outcome}
                        t={t}
                      />
                    </td>
                    <td className="p-2 align-top">
                      <MetricCell
                        value={row.llmJudgeScore}
                        metricKey="llmJudgeScore"
                        benchmarkKind={benchmarkKind}
                        outcome={row.outcome}
                        t={t}
                      />
                    </td>
                    <td className="p-2 align-top">
                      <MetricCell
                        value={row.hallucinationRate}
                        metricKey="hallucinationRate"
                        benchmarkKind={benchmarkKind}
                        outcome={row.outcome}
                        t={t}
                      />
                    </td>
                    <td className="p-2 align-top">
                      <MetricCell
                        value={row.dateCorrectness}
                        metricKey="dateCorrectness"
                        benchmarkKind={benchmarkKind}
                        outcome={row.outcome}
                        t={t}
                      />
                    </td>
                    <td className="text-muted-foreground p-2 align-top">{row.note}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {filteredRows.length > 80 ? (
          <p className="text-muted-foreground text-xs">{t("benchmarkPerItemTruncated", { n: filteredRows.length })}</p>
        ) : null}
      </div>

      {failedSkippedRows.length > 0 ? (
        <div className="space-y-2" data-testid="lab-failed-skipped-section">
          <span className="text-muted-foreground text-xs font-medium">{t("benchmarkFailedSkippedTitle")}</span>
          <div className="max-h-40 overflow-auto rounded-md border">
            <table className="w-full text-left text-xs">
              <thead className="bg-muted/50 sticky top-0">
                <tr>
                  <th className="p-2 font-medium">{t("benchmarkColItem")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColPreset")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColModel")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColOutcome")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColNote")}</th>
                </tr>
              </thead>
              <tbody>
                {failedSkippedRows.slice(0, 40).map((row) => {
                  const snippet = row.question.length > 72 ? `${row.question.slice(0, 72)}…` : row.question;
                  return (
                    <tr key={`failed-${row.id}`} className="border-t border-border">
                      <td className="p-2 align-top">{snippet || "—"}</td>
                      <td className="p-2 align-top">{row.presetLabel !== "—" ? row.presetLabel : "—"}</td>
                      <td className="p-2 align-top">{displayModelId(row.modelId, t)}</td>
                      <td className="p-2 align-top">{formatOutcomeLabel(row.outcome, t)}</td>
                      <td className="text-muted-foreground p-2 align-top">{row.note}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </div>
  );
}
