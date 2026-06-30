"use client";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  downloadCampaignExport,
  downloadCampaignItemsJson,
  downloadCampaignSummaryJson,
  downloadEvaluationFullBundle,
  downloadEvaluationSummaryCsv,
  downloadMvpExport,
  fetchCampaignComparison,
  fetchCampaignItemsBundle,
  fetchEvaluationResultsJson,
  fetchLabCampaignRuns,
  fetchLabEvaluationRun,
  fetchMvpItemsBundle,
  fetchMvpRollupsJson,
} from "@/features/lab/lib/lab-benchmark-results-api";
import {
  aggregateComparisonOutcomeCounts,
  formatMetricCell,
  formatOutcomeLabel,
  formatPresetDisplay,
  isExtensionPreset,
  isMissingMetadata,
  isPresetComparisonAxis,
  normalizeMetadataKey,
  parseComparisonRows,
  formatComparisonScore,
  formatSupportStatusLabel,
  isKnownOutcomeKey,
  resolveComparisonRowLabel,
  resolvePresetKeyFromComparisonRow,
  shouldShowPresetTrend,
  shouldShowTrendEmptyState,
  sortComparisonRows,
  type ComparisonRow,
} from "@/features/lab/lib/lab-benchmark-labels";
import { mapBenchmarkSkipReason } from "@/features/lab/lib/lab-benchmark-skip-reasons";
import { mapUserFacingErrorMessage } from "@/lib/user-facing-error-messages";
import { formatBenchmarkKindLabel, sanitizeLabPrimarySurfaceCopy } from "@/lib/product-copy";
import {
  countOutcomesFromItems,
  readDerivedErrorClassFromItem,
  readGlobalOutcomeCounts,
  readMvpItems,
  readRollupsFromResultsBundle,
  readAnswerableScoreFromComparisonRow,
  readOnExecutedSummary,
} from "@/features/lab/lib/lab-benchmark-mvp-utils";
import { TechnicalDetails } from "@/features/lab/components/compact-lab-ui";
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
  answer: string;
  outcome: string;
  note: string;
  technicalDetail: string;
  presetCode: string;
  presetLabel: string;
  modelId: string;
  snapshotId: string;
  sourcesSummary: string;
  correctness: number | null;
  llmJudgeScore: number | null;
  hallucinationRate: number | null;
  faithfulness: number | null;
  sourceSupport: number | null;
  dateCorrectness: number | null;
  derivedErrorClass: string | null;
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

function findComparisonRowByPresetKey(rows: ComparisonRow[], presetKey: string): ComparisonRow | undefined {
  if (!presetKey) {
    return undefined;
  }
  return rows.find((row) => resolvePresetKeyFromComparisonRow(row) === presetKey);
}

function outcomeCountsFromComparisonRow(row: ComparisonRow | undefined): Record<string, number> {
  if (!row) {
    return {};
  }
  const out: Record<string, number> = {};
  const add = (key: string, value: unknown) => {
    if (typeof value === "number" && Number.isFinite(value) && value >= 0) {
      out[key] = value;
    }
  };
  add("EXECUTED", row.executed);
  add("FAILED", row.failed);
  add("SKIPPED", row.skipped);
  add("NOT_SUPPORTED", row.notSupported);
  return out;
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
  const outcomeFromStatus = typeof item?.status === "string" && item.status.trim() ? item.status.trim() : "";
  const outcome = typeof op?.outcome === "string" && op.outcome ? op.outcome : outcomeFromStatus || "—";
  const unsupportedReason =
    typeof op?.unsupportedReason === "string" && op.unsupportedReason.trim() ? op.unsupportedReason.trim() : "";
  const skipReasonCode =
    typeof op?.skipReasonCode === "string" && op.skipReasonCode.trim()
      ? op.skipReasonCode.trim()
      : typeof item?.failureReason === "string" && item.failureReason.trim()
        ? item.failureReason.trim().split(":")[0]?.trim() ?? ""
        : "";
  const skipReason =
    typeof op?.skipReason === "string" && op.skipReason.trim()
      ? op.skipReason.trim()
      : typeof item?.failureReason === "string"
        ? item.failureReason.trim()
        : "";
  const rawPresetTop = typeof item?.presetCode === "string" ? item.presetCode.trim() : "";
  const rawPreset = typeof op?.presetCode === "string" && op.presetCode.trim() ? op.presetCode.trim() : rawPresetTop;
  const presetCode = rawPreset && !isMissingMetadata(rawPreset) ? rawPreset : "—";
  const rawModelTop = typeof item?.modelLabel === "string" ? item.modelLabel.trim() : "";
  const rawModel = typeof op?.modelId === "string" && op.modelId.trim() ? op.modelId.trim() : rawModelTop;
  const modelId = rawModel && !isMissingMetadata(rawModel) ? rawModel : "—";
  const presetLabelRaw = typeof item?.presetLabel === "string" ? item.presetLabel : "";
  const mp = asRecord(item?.metricsPayload);
  const presetLabelFromPayload = typeof mp?.presetLabel === "string" ? mp.presetLabel : presetLabelRaw;
  const presetLabel = presetCode !== "—" ? formatPresetDisplay(presetCode, presetLabelFromPayload) : "—";
  const question =
    typeof item?.questionText === "string"
      ? item.questionText
      : typeof item?.question === "string"
        ? item.question
        : "";
  const answer =
    typeof item?.actualAnswer === "string"
      ? item.actualAnswer
      : typeof item?.answer === "string"
        ? item.answer
        : "";
  const snapshotId =
    typeof item?.snapshotId === "string" && item.snapshotId.trim()
      ? item.snapshotId.trim()
      : typeof mp?.indexSnapshotId === "string"
        ? mp.indexSnapshotId
        : "—";
  const sourcesRaw = item?.sources;
  let sourcesSummary = "—";
  if (Array.isArray(sourcesRaw) && sourcesRaw.length > 0) {
    sourcesSummary = `${sourcesRaw.length} source(s)`;
  } else if (typeof mp?.retrieved_document_ids === "string" && mp.retrieved_document_ids.trim()) {
    sourcesSummary = mp.retrieved_document_ids.split(";").filter(Boolean).length + " doc(s)";
  }
  const skipMapped = mapBenchmarkSkipReason(
    skipReasonCode || skipReason,
    t,
    formatOutcomeLabel("SKIPPED", t),
  );
  const note =
    isExtensionPreset(presetCode)
      ? t("benchmarkNoteExtension")
      : outcome === "NOT_SUPPORTED"
        ? mapUserFacingErrorMessage(
            unsupportedReason,
            t,
            formatOutcomeLabel("NOT_SUPPORTED", t),
          )
        : outcome === "SKIPPED"
          ? skipMapped.primary
          : outcome === "FAILED"
            ? t("benchmarkNoteSeeExport")
            : "—";
  const technicalDetail =
    outcome === "SKIPPED" || outcome === "FAILED" || outcome === "NOT_SUPPORTED"
      ? skipMapped.technical || unsupportedReason || skipReason || ""
      : "";
  return {
    id:
      typeof item?.itemId === "string" && item.itemId
        ? item.itemId
        : typeof item?.id === "string" && item.id
          ? item.id
          : `row-${idx}`,
    question,
    answer,
    outcome,
    note,
    technicalDetail,
    presetCode,
    presetLabel,
    modelId,
    snapshotId,
    sourcesSummary,
    correctness: numberOrNull(generation?.correctness),
    llmJudgeScore: numberOrNull(generation?.llmJudgeScore),
    hallucinationRate: numberOrNull(generation?.hallucinationRate),
    faithfulness: numberOrNull(generation?.faithfulness),
    sourceSupport: numberOrNull(generation?.sourceSupport),
    dateCorrectness: numberOrNull(generation?.dateCorrectness),
    derivedErrorClass: readDerivedErrorClassFromItem(item),
  };
}

function formatDerivedErrorClassLabel(errorClass: string, t: (key: string) => string): string {
  const key = `benchmarkDerivedErrorClass.${errorClass}`;
  const translated = t(key);
  return translated !== key ? translated : errorClass.replaceAll("_", " ").toLowerCase();
}

function trendRows(
  comparisonRows: ComparisonRow[],
  fallbackRows: ResultTableRow[],
): Array<{ presetCode: string; score: number | null; extension: boolean }> {
  const fromComparison = comparisonRows
    .map((row) => {
      const presetCode = resolvePresetKeyFromComparisonRow(row);
      if (!presetCode) {
        return null;
      }
      return {
        presetCode,
        score: readAnswerableScoreFromComparisonRow(row),
        extension: isExtensionPreset(presetCode),
      };
    })
    .filter((entry): entry is { presetCode: string; score: number | null; extension: boolean } => entry != null);
  if (fromComparison.some((entry) => entry.score != null)) {
    return fromComparison.sort((a, b) => a.presetCode.localeCompare(b.presetCode, undefined, { numeric: true }));
  }
  const grouped = new Map<string, number[]>();
  for (const row of fallbackRows) {
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
  const [errorClassFilter, setErrorClassFilter] = useState("ALL");
  const [selectedComparisonKey, setSelectedComparisonKey] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["lab", "benchmark-mvp-results", runId, campId],
    enabled,
    queryFn: async () => {
      const run = await fetchLabEvaluationRun(runId);
      const effectiveCampaignId =
        campId || (typeof run.campaignId === "string" ? run.campaignId.trim() : "");
      const [resultsBundle, rollupsLegacy, campaignRuns, campaignComparison, campaignItemsBundle] = await Promise.all([
        fetchEvaluationResultsJson(runId).catch(() => fetchMvpItemsBundle(runId)),
        fetchMvpRollupsJson(runId).catch(() => null),
        effectiveCampaignId ? fetchLabCampaignRuns(effectiveCampaignId) : Promise.resolve(null),
        effectiveCampaignId ? fetchCampaignComparison(effectiveCampaignId).catch(() => null) : Promise.resolve(null),
        effectiveCampaignId
          ? fetchCampaignItemsBundle(effectiveCampaignId).catch(() => null)
          : Promise.resolve(null),
      ]);
      const rollups =
        rollupsLegacy && Object.keys(rollupsLegacy).length > 0
          ? rollupsLegacy
          : readRollupsFromResultsBundle(resultsBundle);
      return {
        run,
        rollups,
        itemsBundle: resultsBundle,
        campaignRuns,
        campaignComparison,
        campaignItemsBundle,
        effectiveCampaignId,
      };
    },
  });

  const benchmarkKind = query.data?.run.benchmarkKind ?? null;
  const campaignComparison = query.data?.campaignComparison;
  const effectiveCampaignId = query.data?.effectiveCampaignId?.trim() ?? campId;

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

  const campaignComparisonRecord =
    payload.campaignComparison && typeof payload.campaignComparison === "object"
      ? (payload.campaignComparison as Record<string, unknown>)
      : null;
  const comparisonAxis =
    typeof campaignComparisonRecord?.comparisonAxis === "string"
      ? campaignComparisonRecord.comparisonAxis
      : "";
  const isCampaignMode = effectiveCampaignId.length > 0;
  const campaignItemRows =
    payload.campaignItemsBundle && typeof payload.campaignItemsBundle === "object"
      ? readMvpItems(payload.campaignItemsBundle)
      : [];
  const useCampaignItems = isCampaignMode && campaignItemRows.length > 0;
  const mvpRows = useCampaignItems ? campaignItemRows : readMvpItems(payload.itemsBundle);
  const tableRows = mvpRows.map((row, idx) => toResultTableRow(row, idx, t));
  const campaignOutcomeCounts =
    comparisonRows.length > 0
      ? aggregateComparisonOutcomeCounts(comparisonRows)
      : countOutcomesFromItems(mvpRows);
  const runOutcomeCounts = readGlobalOutcomeCounts(payload.rollups);
  const effectivePresetScope =
    selectedComparisonKey ?? (presetFilter !== "ALL" ? presetFilter : null);
  const scopedComparisonRow = effectivePresetScope
    ? findComparisonRowByPresetKey(comparisonRows, effectivePresetScope)
    : undefined;
  const scopedOutcomeCounts = outcomeCountsFromComparisonRow(scopedComparisonRow);
  const occ = isCampaignMode
    ? effectivePresetScope
      ? Object.keys(scopedOutcomeCounts).length > 0
        ? scopedOutcomeCounts
        : countOutcomesFromItems(
            tableRows.filter((row) => row.presetCode === effectivePresetScope),
          )
      : campaignOutcomeCounts
    : runOutcomeCounts;
  const executedOutcomeCount = occ.EXECUTED ?? 0;
  const campaignExecutedTotal = campaignOutcomeCounts.EXECUTED ?? 0;
  const macroExecuted = readOnExecutedSummary(payload.rollups.globalMacro);
  const modelOptions = sortedUnique(tableRows.map((row) => row.modelId).filter((id) => id !== "—"));
  const presetOptions = isPresetComparisonAxis(comparisonAxis) && comparisonRows.length > 0
    ? comparisonRows
        .map((row) => {
          const key = resolvePresetKeyFromComparisonRow(row);
          const label = resolveComparisonRowLabel(row, comparisonAxis);
          return key ? { key, label } : null;
        })
        .filter((entry): entry is { key: string; label: string } => entry != null)
    : sortedUnique(tableRows.map((row) => row.presetCode).filter((code) => code !== "—")).map((code) => {
        const sample = tableRows.find((row) => row.presetCode === code);
        return { key: code, label: sample?.presetLabel && sample.presetLabel !== "—" ? sample.presetLabel : code };
      });
  const errorClassOptions = sortedUnique(
    tableRows.map((row) => row.derivedErrorClass).filter((value): value is string => value != null && value.length > 0),
  );
  const filteredRows = sortByCorrectnessDesc(
    tableRows.filter(
      (row) =>
        (modelFilter === "ALL" || row.modelId === modelFilter) &&
        (presetFilter === "ALL" || row.presetCode === presetFilter) &&
        (errorClassFilter === "ALL" ||
          (row.derivedErrorClass != null && row.derivedErrorClass === errorClassFilter)),
    ),
  );
  const executedRows = filteredRows.filter((row) => row.outcome === "EXECUTED");
  const failedSkippedRows = filteredRows.filter((row) => FAILED_SKIPPED_OUTCOMES.has(row.outcome));
  const trend = trendRows(comparisonRows, filteredRows);
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

  const knowledgeBaseFromRuns = payload.campaignRuns?.find((r) => {
    const row = r as Record<string, unknown>;
    return typeof row.corpusName === "string" && row.corpusName.trim().length > 0;
  }) as Record<string, unknown> | undefined;
  const knowledgeBaseFromItems = mvpRows.find((row) => {
    if (!row || typeof row !== "object") return false;
    const kb = (row as Record<string, unknown>).knowledgeBaseName;
    return typeof kb === "string" && kb.trim().length > 0;
  }) as Record<string, unknown> | undefined;
  const knowledgeBaseLabelRaw =
    (typeof knowledgeBaseFromRuns?.corpusName === "string" ? knowledgeBaseFromRuns.corpusName.trim() : "") ||
    (typeof knowledgeBaseFromItems?.knowledgeBaseName === "string"
      ? knowledgeBaseFromItems.knowledgeBaseName.trim()
      : "") ||
    null;
  const knowledgeBaseLabel = knowledgeBaseLabelRaw
    ? sanitizeLabPrimarySurfaceCopy(knowledgeBaseLabelRaw, t("labCorpusTitle"))
    : null;

  return (
    <div className="space-y-4 rounded-md border bg-muted/20 p-4" data-testid="lab-benchmark-results-panel">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-2">
          <Label className="text-base">{t("benchmarkResultsTitle")}</Label>
          <p className="text-muted-foreground text-xs">
            {t("benchmarkResultsRunLine", {
              id: payload.run.id.slice(0, 8),
              status: payload.run.status,
              kind: formatBenchmarkKindLabel(payload.run.benchmarkKind, t),
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
          {knowledgeBaseLabel ? (
            <p className="text-muted-foreground text-xs" data-testid="lab-benchmark-knowledge-base-label">
              {t("benchmarkResultsKnowledgeBase", { name: knowledgeBaseLabel })}
            </p>
          ) : null}
        </div>
        <div className="flex flex-col items-end gap-2" data-testid="lab-benchmark-export-actions">
          <div className="flex flex-wrap justify-end gap-2" data-testid="lab-benchmark-export-primary">
            <Button
              type="button"
              variant="default"
              size="sm"
              data-testid="lab-export-primary-json"
              onClick={() => void downloadMvpExport(payload.run.id, "results.json")}
            >
              {t("benchmarkDownloadJson")}
            </Button>
            <Button
              type="button"
              variant="default"
              size="sm"
              data-testid="lab-export-primary-csv"
              onClick={() => void downloadEvaluationSummaryCsv(payload.run.id)}
            >
              {t("benchmarkDownloadCsvSummary")}
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              data-testid="lab-export-v1-full-bundle"
              onClick={() => void downloadEvaluationFullBundle(payload.run.id)}
            >
              {t("benchmarkDownloadFullBundle")}
            </Button>
          </div>
          <TechnicalDetails
            summary={t("benchmarkExportsAdvancedSummary")}
            testId="lab-benchmark-export-advanced"
            className="w-full max-w-xl rounded-md border bg-muted/20 p-3 text-xs"
          >
            <div className="flex flex-wrap gap-2">
              {effectiveCampaignId ? (
                <>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    data-testid="lab-export-campaign-items-json"
                    onClick={() => void downloadCampaignItemsJson(effectiveCampaignId)}
                  >
                    {t("benchmarkExportCampaignItemsJson")}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    data-testid="lab-export-campaign-summary-json"
                    onClick={() => void downloadCampaignSummaryJson(effectiveCampaignId)}
                  >
                    {t("benchmarkExportCampaignSummaryJson")}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    data-testid="lab-export-campaign-items-csv"
                    onClick={() => void downloadCampaignExport(effectiveCampaignId, "items.csv")}
                  >
                    {t("benchmarkExportCampaignItemsCsv")}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    data-testid="lab-export-campaign-summary-csv"
                    onClick={() => void downloadCampaignExport(effectiveCampaignId, "summary.csv")}
                  >
                    {t("benchmarkExportCampaignSummaryCsv")}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    data-testid="lab-export-campaign-bundle-json"
                    onClick={() => void downloadCampaignExport(effectiveCampaignId, "bundle.json")}
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
            <details data-testid="lab-benchmark-raw-rollups-preview">
              <summary className="cursor-pointer font-medium text-foreground">{t("benchmarkRawDataSummary")}</summary>
              <pre className="text-muted-foreground mt-2 max-h-48 overflow-auto whitespace-pre-wrap rounded-md border bg-background/60 p-2 font-mono text-[10px]">
                {JSON.stringify(payload.rollups, null, 2)}
              </pre>
            </details>
          </TechnicalDetails>
        </div>
      </div>

      {effectiveCampaignId && payload.campaignRuns && payload.campaignRuns.length > 0 ? (
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
                const presetCode = typeof row.presetCode === "string" ? row.presetCode.trim() : "";
                const presetLabel = typeof row.presetLabel === "string" ? row.presetLabel.trim() : "";
                const preset =
                  presetLabel && presetCode
                    ? formatPresetDisplay(presetCode, presetLabel)
                    : presetLabel || presetCode;
                const axisLabel =
                  isPresetComparisonAxis(comparisonAxis) && preset ? preset : preset || model;
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

      {effectiveCampaignId && campaignComparisonRecord ? (
        comparisonRows.length >= 2 ? (
        <div
          className="space-y-2"
          data-testid="lab-campaign-comparison-panel"
          data-comparison-axis={comparisonAxis || undefined}
        >
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
                  <th
                    className="bg-primary/5 p-2 font-semibold"
                    title={t("benchmarkColAnswerableScoreTooltip")}
                  >
                    {t("benchmarkColAnswerableScore")}
                  </th>
                  <th className="p-2 font-medium" title={t("benchmarkColGlobalScoreTooltip")}>
                    {t("benchmarkColGlobalScore")}
                  </th>
                  <th className="p-2 font-medium">{t("benchmarkColExact")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColSemantic")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColRecall")}</th>
                  <th className="p-2 font-medium">{t("benchmarkColLatency")}</th>
                </tr>
              </thead>
              <tbody>
                {comparisonRows.slice(0, 50).map((r, idx) => {
                  const presetKey = resolvePresetKeyFromComparisonRow(r);
                  const label = resolveComparisonRowLabel(r, comparisonAxis);
                  const selected = presetKey.length > 0 && selectedComparisonKey === presetKey;
                  return (
                    <tr
                      key={`${presetKey || label}-${idx}`}
                      className={
                        selected
                          ? "border-border cursor-pointer border-t bg-primary/10"
                          : "border-border hover:bg-muted/30 cursor-pointer border-t"
                      }
                      data-testid={`lab-comparison-row-${idx}`}
                      data-comparison-key={presetKey || undefined}
                      onClick={() => {
                        if (!presetKey) return;
                        const next = selectedComparisonKey === presetKey ? null : presetKey;
                        setSelectedComparisonKey(next);
                        setPresetFilter(next ?? "ALL");
                      }}
                    >
                      <td className="p-2">
                        <div>{label}</div>
                        {formatSupportStatusLabel(
                          typeof r.benchmarkSupportStatus === "string" ? r.benchmarkSupportStatus : undefined,
                          t,
                        ) ? (
                          <div className="text-muted-foreground text-[10px]">
                            {formatSupportStatusLabel(
                              typeof r.benchmarkSupportStatus === "string" ? r.benchmarkSupportStatus : undefined,
                              t,
                            )}
                          </div>
                        ) : null}
                      </td>
                      <td className="p-2">{String(r.totalItems ?? "—")}</td>
                      <td className="p-2">{String(r.executed ?? "—")}</td>
                      <td className="p-2">{String(r.notSupported ?? "—")}</td>
                      <td className="p-2">{String(r.failed ?? "—")}</td>
                      <td className="p-2">{String(r.skipped ?? "—")}</td>
                      <td className="bg-primary/5 p-2 font-mono font-semibold text-primary">
                        {formatComparisonScore(r.scoreAnswerable)}
                      </td>
                      <td className="p-2 font-mono" title={t("benchmarkColGlobalScoreTooltip")}>
                        {formatComparisonScore(r.scoreGlobal)}
                      </td>
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
          <p className="text-muted-foreground text-[11px]">{t("benchmarkScoreInterpretationHint")}</p>
        </div>
        ) : (
          <output className="text-muted-foreground block text-xs" data-testid="lab-campaign-comparison-empty">
            {t("benchmarkCampaignComparisonEmpty")}
          </output>
        )
      ) : null}

      {isCampaignMode && comparisonRows.length >= 2 && !effectivePresetScope ? (
        <p className="text-muted-foreground text-[11px]" data-testid="lab-campaign-scope-hint">
          {t("benchmarkCampaignScopeHint")}
        </p>
      ) : null}

      {isCampaignMode &&
      !effectivePresetScope &&
      campaignExecutedTotal > 0 &&
      ((campaignOutcomeCounts.SKIPPED ?? 0) > 0 ||
        (campaignOutcomeCounts.FAILED ?? 0) > 0 ||
        (campaignOutcomeCounts.NOT_SUPPORTED ?? 0) > 0) ? (
        <output className="text-muted-foreground block text-xs" data-testid="lab-campaign-partial-summary">
          {t("benchmarkCampaignPartialSummary", {
            executed: String(campaignExecutedTotal),
            skipped: String(campaignOutcomeCounts.SKIPPED ?? 0),
            failed: String(campaignOutcomeCounts.FAILED ?? 0),
            notSupported: String(campaignOutcomeCounts.NOT_SUPPORTED ?? 0),
          })}
        </output>
      ) : null}

      {executedOutcomeCount <= 0 ? (
        effectivePresetScope ? (
          <output
            role="status"
            className="text-muted-foreground block text-xs"
            data-testid="lab-benchmark-scope-no-executed"
          >
            {t("benchmarkScopeNoExecutedItems")}
          </output>
        ) : isCampaignMode && campaignExecutedTotal > 0 ? null : (
          <output
            role="alert"
            className="text-destructive block text-xs font-medium"
            data-testid="lab-benchmark-no-executed-warning"
          >
            {t("benchmarkNoExecutedItemsSummary")}
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
                {isKnownOutcomeKey(k) ? formatOutcomeLabel(k, t) : t("benchmarkOutcomeLabel.unknown")}: {n}
              </span>
            ))}
        </div>
      </div>

      {hasExtensionPresets ? (
        <p className="text-muted-foreground text-[11px]" data-testid="lab-extension-legend">
          {t("benchmarkExtensionLegend")}
        </p>
      ) : null}

      <div
        className={`grid gap-3 ${errorClassOptions.length > 0 ? "md:grid-cols-2 lg:grid-cols-3" : "md:grid-cols-2"}`}
        data-testid="lab-results-filters"
      >
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
            onChange={(event) => {
              const value = event.target.value;
              setPresetFilter(value);
              setSelectedComparisonKey(value === "ALL" ? null : value);
            }}
          >
            <option value="ALL">{t("benchmarkFilterAll")}</option>
            {presetOptions.map((preset) => (
              <option key={preset.key} value={preset.key}>
                {preset.label}
              </option>
            ))}
          </select>
        </label>
        {errorClassOptions.length > 0 ? (
          <label className="space-y-1 text-xs">
            <span className="text-muted-foreground font-medium">{t("benchmarkFilterErrorClass")}</span>
            <select
              className="bg-background w-full rounded-md border px-2 py-1"
              data-testid="lab-results-filter-error-class"
              value={errorClassFilter}
              onChange={(event) => setErrorClassFilter(event.target.value)}
            >
              <option value="ALL">{t("benchmarkFilterAll")}</option>
              {errorClassOptions.map((errorClass) => (
                <option key={errorClass} value={errorClass}>
                  {formatDerivedErrorClassLabel(errorClass, t)}
                </option>
              ))}
            </select>
          </label>
        ) : null}
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
                <th className="p-2 font-medium">{t("benchmarkColAnswer")}</th>
                <th className="p-2 font-medium">{t("benchmarkColSources")}</th>
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
                    <td className="p-2 align-top" title={row.answer}>
                      {row.answer.length > 64 ? `${row.answer.slice(0, 64)}…` : row.answer || "—"}
                    </td>
                    <td className="p-2 align-top">{row.sourcesSummary}</td>
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
                      <td className="text-muted-foreground p-2 align-top">
                        <span>{row.note}</span>
                        {row.technicalDetail ? (
                          <details className="mt-1" data-testid={`lab-item-technical-${row.id}`}>
                            <summary className="cursor-pointer text-[10px]">
                              {t("benchmarkTechnicalDetailsSummary")}
                            </summary>
                            <pre className="text-muted-foreground mt-1 max-w-xs overflow-auto whitespace-pre-wrap font-mono text-[10px]">
                              {row.technicalDetail}
                            </pre>
                          </details>
                        ) : null}
                      </td>
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
