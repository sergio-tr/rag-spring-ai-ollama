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
import { EmbeddingComparisonTable } from "@/features/lab/components/embedding-comparison-table";
import { LlmComparisonTable } from "@/features/lab/components/llm-comparison-table";
import { RagComparisonTable } from "@/features/lab/components/rag-comparison-table";
import {
  LabComparisonTable,
  LabComparisonTableHead,
  LabComparisonTh,
} from "@/features/lab/components/lab-comparison-table";
import { enrichComparisonRowsFromItems } from "@/features/lab/lib/lab-comparison-metrics";
import {
  embeddingRowMatchesGoldFilter,
  embeddingRowMatchesHitFilter,
  embeddingRowMatchesQueryFilter,
  formatHitIndicator,
  recommendEmbeddingModel,
  sortEmbeddingComparisonRows,
  sortEmbeddingItemRows,
  toEmbeddingItemRow,
  type EmbeddingItemRow,
} from "@/features/lab/lib/embedding-result-table";
import { paginateRows, type LabTablePageSize, LAB_TABLE_DEFAULT_PAGE_SIZE } from "@/features/lab/lib/lab-table-pagination";
import { LabTablePaginationBar } from "@/features/lab/components/lab-table-pagination";
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
  const rawModelTop =
    typeof item?.embeddingModelId === "string" && item.embeddingModelId.trim()
      ? item.embeddingModelId.trim()
      : typeof item?.llmModelId === "string" && item.llmModelId.trim()
        ? item.llmModelId.trim()
        : typeof item?.modelLabel === "string"
          ? item.modelLabel.trim()
          : "";
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
  const [hitMissFilter, setHitMissFilter] = useState("ALL");
  const [queryTextFilter, setQueryTextFilter] = useState("");
  const [goldTextFilter, setGoldTextFilter] = useState("");
  const [errorClassFilter, setErrorClassFilter] = useState("ALL");
  const [outcomeFilter, setOutcomeFilter] = useState("ALL");
  const [selectedComparisonKey, setSelectedComparisonKey] = useState<string | null>(null);
  const [perItemPage, setPerItemPage] = useState(1);
  const [perItemPageSize, setPerItemPageSize] = useState<LabTablePageSize>(LAB_TABLE_DEFAULT_PAGE_SIZE);
  const [failedPage, setFailedPage] = useState(1);
  const [failedPageSize, setFailedPageSize] = useState<LabTablePageSize>(LAB_TABLE_DEFAULT_PAGE_SIZE);
  const [fallbackComparisonPage, setFallbackComparisonPage] = useState(1);
  const [fallbackComparisonPageSize, setFallbackComparisonPageSize] =
    useState<LabTablePageSize>(LAB_TABLE_DEFAULT_PAGE_SIZE);
  const [campaignRunsPage, setCampaignRunsPage] = useState(1);
  const [campaignRunsPageSize, setCampaignRunsPageSize] =
    useState<LabTablePageSize>(LAB_TABLE_DEFAULT_PAGE_SIZE);

  const paginationResetKey = `${modelFilter}:${presetFilter}:${hitMissFilter}:${queryTextFilter}:${goldTextFilter}:${errorClassFilter}:${outcomeFilter}:${runId}`;
  const [paginationEpoch, setPaginationEpoch] = useState(paginationResetKey);
  if (paginationEpoch !== paginationResetKey) {
    setPaginationEpoch(paginationResetKey);
    setPerItemPage(1);
    setFailedPage(1);
    setFallbackComparisonPage(1);
    setCampaignRunsPage(1);
  }

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
    const kind = query.data?.run.benchmarkKind ?? null;
    const parsed =
      kind === "EMBEDDING_RETRIEVAL"
        ? sortEmbeddingComparisonRows(parseComparisonRows(campaignComparison))
        : sortComparisonRows(parseComparisonRows(campaignComparison));
    const campaignItemRows =
      query.data?.campaignItemsBundle && typeof query.data.campaignItemsBundle === "object"
        ? readMvpItems(query.data.campaignItemsBundle)
        : [];
    if (campaignItemRows.length === 0) {
      return parsed;
    }
    return enrichComparisonRowsFromItems(parsed, campaignItemRows);
  }, [campaignComparison, query.data]);

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
  const embeddingItemRows =
    benchmarkKind === "EMBEDDING_RETRIEVAL" ? mvpRows.map((row, idx) => toEmbeddingItemRow(row, idx)) : [];
  const embeddingRecommendation =
    benchmarkKind === "EMBEDDING_RETRIEVAL" && comparisonRows.length >= 2
      ? recommendEmbeddingModel(comparisonRows)
      : null;
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
  const modelOptions =
    benchmarkKind === "EMBEDDING_RETRIEVAL"
      ? sortedUnique(embeddingItemRows.map((row) => row.embeddingModelId).filter((id) => id !== "—"))
      : sortedUnique(tableRows.map((row) => row.modelId).filter((id) => id !== "—"));
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
        (benchmarkKind === "EMBEDDING_RETRIEVAL" || presetFilter === "ALL" || row.presetCode === presetFilter) &&
        (outcomeFilter === "ALL" || row.outcome === outcomeFilter) &&
        (errorClassFilter === "ALL" ||
          (row.derivedErrorClass != null && row.derivedErrorClass === errorClassFilter)),
    ),
  );
  const filteredEmbeddingRows =
    benchmarkKind === "EMBEDDING_RETRIEVAL"
      ? sortEmbeddingItemRows(
          embeddingItemRows.filter(
            (row) =>
              (modelFilter === "ALL" || row.embeddingModelId === modelFilter) &&
              (outcomeFilter === "ALL" || row.outcome === outcomeFilter) &&
              embeddingRowMatchesHitFilter(row, hitMissFilter) &&
              embeddingRowMatchesQueryFilter(row, queryTextFilter) &&
              embeddingRowMatchesGoldFilter(row, goldTextFilter),
          ),
        )
      : [];
  const executedRows =
    benchmarkKind === "EMBEDDING_RETRIEVAL"
      ? filteredEmbeddingRows.filter((row) => row.outcome === "EXECUTED")
      : filteredRows.filter((row) => row.outcome === "EXECUTED");
  const failedSkippedRows =
    benchmarkKind === "EMBEDDING_RETRIEVAL"
      ? filteredEmbeddingRows.filter((row) => FAILED_SKIPPED_OUTCOMES.has(row.outcome))
      : filteredRows.filter((row) => FAILED_SKIPPED_OUTCOMES.has(row.outcome));
  const perItemSlice =
    benchmarkKind === "EMBEDDING_RETRIEVAL"
      ? paginateRows(executedRows as EmbeddingItemRow[], perItemPage, perItemPageSize)
      : paginateRows(executedRows as ResultTableRow[], perItemPage, perItemPageSize);
  const failedSlice =
    benchmarkKind === "EMBEDDING_RETRIEVAL"
      ? paginateRows(failedSkippedRows as EmbeddingItemRow[], failedPage, failedPageSize)
      : paginateRows(failedSkippedRows as ResultTableRow[], failedPage, failedPageSize);
  const fallbackComparisonSlice = paginateRows(comparisonRows, fallbackComparisonPage, fallbackComparisonPageSize);
  const campaignRuns = payload.campaignRuns ?? [];
  const campaignRunsSlice = paginateRows(campaignRuns, campaignRunsPage, campaignRunsPageSize);
  const outcomeOptions = sortedUnique(tableRows.map((row) => row.outcome).filter((value) => value !== "—"));
  const trend = trendRows(comparisonRows, benchmarkKind === "EMBEDDING_RETRIEVAL" ? [] : filteredRows);
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
          {macroExecuted && macroExecuted.n > 0 && benchmarkKind !== "EMBEDDING_RETRIEVAL" ? (
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
            {benchmarkKind === "EMBEDDING_RETRIEVAL"
              ? t("benchmarkResultsCountsLineEmbedding", {
                  items: mvpRows.length,
                  questions: uniqueQuestionIds.size,
                })
              : t("benchmarkResultsCountsLine", {
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

      {effectiveCampaignId && campaignRuns.length > 0 ? (
        <div className="space-y-2" data-testid="lab-campaign-runs-panel">
          <span className="text-muted-foreground text-xs font-medium">{t("benchmarkCampaignRunsTitle")}</span>
          <div className="max-h-40 overflow-auto rounded-md border bg-background/40 p-2 text-xs">
            <ul className="space-y-1">
              {campaignRunsSlice.pageRows.map((r, idx) => {
                const row = r as Record<string, unknown>;
                const rid = typeof row.runId === "string" ? row.runId : `row-${idx}`;
                const model =
                  benchmarkKind === "EMBEDDING_RETRIEVAL" &&
                  typeof row.embeddingModelId === "string" &&
                  row.embeddingModelId.trim()
                    ? displayModelId(row.embeddingModelId, t)
                    : benchmarkKind === "LLM_JUDGE_QA" &&
                        typeof row.llmModelId === "string" &&
                        row.llmModelId.trim()
                      ? displayModelId(row.llmModelId, t)
                      : typeof row.modelLabel === "string" && row.modelLabel
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
          </div>
          <LabTablePaginationBar
            page={campaignRunsSlice.page}
            pageSize={campaignRunsSlice.pageSize}
            totalRows={campaignRunsSlice.totalRows}
            totalPages={campaignRunsSlice.totalPages}
            rangeStart={campaignRunsSlice.rangeStart}
            rangeEnd={campaignRunsSlice.rangeEnd}
            onPageChange={setCampaignRunsPage}
            onPageSizeChange={setCampaignRunsPageSize}
            testId="lab-campaign-runs-pagination"
          />
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
          {benchmarkKind === "EMBEDDING_RETRIEVAL" ? (
            <EmbeddingComparisonTable
              rows={comparisonRows}
              comparisonAxis={comparisonAxis}
              selectedKey={selectedComparisonKey}
              onSelectRow={(axisValue) => {
                const next = selectedComparisonKey === axisValue ? null : axisValue;
                setSelectedComparisonKey(next);
                setModelFilter(next ?? "ALL");
              }}
            />
          ) : benchmarkKind === "LLM_JUDGE_QA" ? (
            <LlmComparisonTable
              rows={comparisonRows}
              comparisonAxis={comparisonAxis}
              selectedKey={selectedComparisonKey}
              onSelectRow={(axisValue) => {
                const next = selectedComparisonKey === axisValue ? null : axisValue;
                setSelectedComparisonKey(next);
                setModelFilter(next ?? "ALL");
              }}
            />
          ) : benchmarkKind === "RAG_PRESET_END_TO_END" ? (
            <RagComparisonTable
              rows={comparisonRows}
              comparisonAxis={comparisonAxis}
              selectedKey={selectedComparisonKey}
              onSelectRow={(presetKey) => {
                const next = selectedComparisonKey === presetKey ? null : presetKey;
                setSelectedComparisonKey(next);
                setPresetFilter(next ?? "ALL");
              }}
            />
          ) : (
            <LabComparisonTable>
              <LabComparisonTableHead>
                <tr>
                  <LabComparisonTh>{t("benchmarkColComparisonLabel")}</LabComparisonTh>
                  <LabComparisonTh>{t("benchmarkColTotal")}</LabComparisonTh>
                  <LabComparisonTh>{t("benchmarkColExecuted")}</LabComparisonTh>
                  <LabComparisonTh>{t("benchmarkColNotSupported")}</LabComparisonTh>
                  <LabComparisonTh>{t("benchmarkColFailed")}</LabComparisonTh>
                  <LabComparisonTh>{t("benchmarkColSkipped")}</LabComparisonTh>
                  <LabComparisonTh
                    className="bg-primary/5 font-semibold"
                    title={t("benchmarkColAnswerableScoreTooltip")}
                  >
                    {t("benchmarkColAnswerableScore")}
                  </LabComparisonTh>
                  <LabComparisonTh title={t("benchmarkColGlobalScoreTooltip")}>
                    {t("benchmarkColGlobalScore")}
                  </LabComparisonTh>
                  <LabComparisonTh>{t("benchmarkColExact")}</LabComparisonTh>
                  <LabComparisonTh>{t("benchmarkColSemantic")}</LabComparisonTh>
                  <LabComparisonTh>{t("benchmarkColRecall")}</LabComparisonTh>
                  <LabComparisonTh>{t("benchmarkColLatency")}</LabComparisonTh>
                </tr>
              </LabComparisonTableHead>
              <tbody>
                {fallbackComparisonSlice.pageRows.map((r, idx) => {
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
            </LabComparisonTable>
          )}
          {benchmarkKind !== "EMBEDDING_RETRIEVAL" &&
          benchmarkKind !== "LLM_JUDGE_QA" &&
          benchmarkKind !== "RAG_PRESET_END_TO_END" &&
          comparisonRows.length > 0 ? (
            <LabTablePaginationBar
              page={fallbackComparisonSlice.page}
              pageSize={fallbackComparisonSlice.pageSize}
              totalRows={fallbackComparisonSlice.totalRows}
              totalPages={fallbackComparisonSlice.totalPages}
              rangeStart={fallbackComparisonSlice.rangeStart}
              rangeEnd={fallbackComparisonSlice.rangeEnd}
              onPageChange={setFallbackComparisonPage}
              onPageSizeChange={setFallbackComparisonPageSize}
              testId="lab-fallback-comparison-pagination"
            />
          ) : null}
          {benchmarkKind === "EMBEDDING_RETRIEVAL" && embeddingRecommendation ? (
            <div
              className="rounded-md border border-primary/20 bg-primary/5 p-3 text-xs"
              data-testid="lab-embedding-recommendation"
            >
              <p className="font-medium text-foreground">{t("benchmarkEmbeddingRecommendationTitle")}</p>
              <p className="mt-1">
                <span className="font-mono">{embeddingRecommendation.bestModelId}</span> —{" "}
                {t(embeddingRecommendation.bestReasonKey)}{" "}
                {t("benchmarkEmbeddingRecommendMetrics", embeddingRecommendation.bestMetrics)}
              </p>
              {embeddingRecommendation.latencyModelId && embeddingRecommendation.latencyReasonKey ? (
                <p className="text-muted-foreground mt-1">
                  <span className="font-mono">{embeddingRecommendation.latencyModelId}</span> —{" "}
                  {t(embeddingRecommendation.latencyReasonKey)}
                  {embeddingRecommendation.latencyMs ? ` (${embeddingRecommendation.latencyMs} ms)` : ""}
                </p>
              ) : null}
            </div>
          ) : null}
          <p className="text-muted-foreground text-[11px]">{t("benchmarkCampaignComparisonHint")}</p>
          {benchmarkKind !== "EMBEDDING_RETRIEVAL" ? (
            <p className="text-muted-foreground text-[11px]">{t("benchmarkScoreInterpretationHint")}</p>
          ) : null}
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
        className={`grid gap-3 ${
          benchmarkKind === "EMBEDDING_RETRIEVAL"
            ? "md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5"
            : errorClassOptions.length > 0
              ? "md:grid-cols-2 lg:grid-cols-4"
              : "md:grid-cols-2 lg:grid-cols-3"
        }`}
        data-testid="lab-results-filters"
      >
        <label className="space-y-1 text-xs">
          <span className="text-muted-foreground font-medium">
            {benchmarkKind === "EMBEDDING_RETRIEVAL"
              ? t("benchmarkFilterEmbeddingModel")
              : t("benchmarkFilterModel")}
          </span>
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
        {benchmarkKind !== "EMBEDDING_RETRIEVAL" ? (
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
        ) : null}
        {benchmarkKind === "EMBEDDING_RETRIEVAL" ? (
          <>
            <label className="space-y-1 text-xs">
              <span className="text-muted-foreground font-medium">{t("benchmarkFilterHitMiss")}</span>
              <select
                className="bg-background w-full rounded-md border px-2 py-1"
                data-testid="lab-results-filter-hit-miss"
                value={hitMissFilter}
                onChange={(event) => setHitMissFilter(event.target.value)}
              >
                <option value="ALL">{t("benchmarkFilterAll")}</option>
                <option value="HIT">{t("benchmarkFilterHit")}</option>
                <option value="MISS">{t("benchmarkFilterMiss")}</option>
              </select>
            </label>
            <label className="space-y-1 text-xs">
              <span className="text-muted-foreground font-medium">{t("benchmarkFilterQuery")}</span>
              <input
                className="bg-background w-full rounded-md border px-2 py-1"
                data-testid="lab-results-filter-query"
                value={queryTextFilter}
                onChange={(event) => setQueryTextFilter(event.target.value)}
                placeholder={t("benchmarkFilterQuery")}
              />
            </label>
            <label className="space-y-1 text-xs">
              <span className="text-muted-foreground font-medium">{t("benchmarkFilterGold")}</span>
              <input
                className="bg-background w-full rounded-md border px-2 py-1"
                data-testid="lab-results-filter-gold"
                value={goldTextFilter}
                onChange={(event) => setGoldTextFilter(event.target.value)}
                placeholder={t("benchmarkFilterGold")}
              />
            </label>
          </>
        ) : null}
        <label className="space-y-1 text-xs">
          <span className="text-muted-foreground font-medium">{t("benchmarkFilterOutcome")}</span>
          <select
            className="bg-background w-full rounded-md border px-2 py-1"
            data-testid="lab-results-filter-outcome"
            value={outcomeFilter}
            onChange={(event) => setOutcomeFilter(event.target.value)}
          >
            <option value="ALL">{t("benchmarkFilterAll")}</option>
            {outcomeOptions.map((outcome) => (
              <option key={outcome} value={outcome}>
                {formatOutcomeLabel(outcome, t)}
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
        {benchmarkKind === "EMBEDDING_RETRIEVAL" ? (
          <LabComparisonTable maxHeightClassName="max-h-56" testId="lab-benchmark-per-item-table">
            <LabComparisonTableHead>
              <tr>
                <LabComparisonTh>{t("benchmarkColItem")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColEmbeddingModel")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColExpectedGold")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColRetrievedTop1")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColGoldRank")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColHitAt1")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColHitAt3")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColHitAt5")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColScoreDistance")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColLatency")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColOutcome")}</LabComparisonTh>
              </tr>
            </LabComparisonTableHead>
            <tbody>
              {(perItemSlice.pageRows as EmbeddingItemRow[]).map((row) => {
                const snippet = row.question.length > 96 ? `${row.question.slice(0, 96)}…` : row.question;
                return (
                  <tr key={row.id} className="border-t border-border" data-testid={`lab-item-row-${row.id}`}>
                    <td className="p-2 align-top">{snippet || "—"}</td>
                    <td className="p-2 align-top">{displayModelId(row.embeddingModelId, t)}</td>
                    <td className="p-2 align-top" title={row.expectedGold}>
                      {row.expectedGold}
                    </td>
                    <td className="p-2 align-top" title={row.retrievedTop1}>
                      {row.retrievedTop1}
                    </td>
                    <td className="p-2 align-top font-mono">{row.goldRank == null ? "—" : String(row.goldRank)}</td>
                    <td className="p-2 align-top font-mono">{formatHitIndicator(row.hitAt1)}</td>
                    <td className="p-2 align-top font-mono">{formatHitIndicator(row.hitAt3)}</td>
                    <td className="p-2 align-top font-mono">{formatHitIndicator(row.hitAt5)}</td>
                    <td className="p-2 align-top font-mono">
                      {row.topScore == null ? "—" : row.topScore.toFixed(4)}
                    </td>
                    <td className="p-2 align-top font-mono">
                      {row.latencyMs == null ? "—" : String(Math.round(row.latencyMs))}
                    </td>
                    <td className="p-2 align-top">{formatOutcomeLabel(row.outcome, t)}</td>
                  </tr>
                );
              })}
            </tbody>
          </LabComparisonTable>
        ) : (
          <LabComparisonTable maxHeightClassName="max-h-56" testId="lab-benchmark-per-item-table">
            <LabComparisonTableHead>
              <tr>
                <LabComparisonTh>{t("benchmarkColItem")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColPreset")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColModel")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColAnswer")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColSources")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColOutcome")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColCorrectness")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColJudge")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColHallucination")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColDate")}</LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColNote")}</LabComparisonTh>
              </tr>
            </LabComparisonTableHead>
            <tbody>
              {(perItemSlice.pageRows as ResultTableRow[]).map((row) => {
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
          </LabComparisonTable>
        )}
        {benchmarkKind === "EMBEDDING_RETRIEVAL" &&
        (perItemSlice.pageRows as EmbeddingItemRow[]).some((row) => row.hasDownstreamAnswer) ? (
          <details className="text-xs" data-testid="lab-embedding-answer-step-details">
            <summary className="cursor-pointer font-medium">{t("benchmarkEmbeddingAnswerStepTitle")}</summary>
            <div className="mt-2 space-y-2">
              {(perItemSlice.pageRows as EmbeddingItemRow[])
                .filter((row) => row.hasDownstreamAnswer)
                .map((row) => (
                  <div key={`answer-${row.id}`} className="rounded-md border bg-background/40 p-2">
                    <p className="font-mono text-[11px]">
                      {t("benchmarkEmbeddingAnswerModel")}: {displayModelId(row.answerModelId, t)}
                    </p>
                    <p className="text-muted-foreground mt-1">{row.answer}</p>
                  </div>
                ))}
            </div>
          </details>
        ) : null}
        <LabTablePaginationBar
          page={perItemSlice.page}
          pageSize={perItemSlice.pageSize}
          totalRows={perItemSlice.totalRows}
          totalPages={perItemSlice.totalPages}
          rangeStart={perItemSlice.rangeStart}
          rangeEnd={perItemSlice.rangeEnd}
          onPageChange={setPerItemPage}
          onPageSizeChange={setPerItemPageSize}
          testId="lab-benchmark-per-item-pagination"
        />
      </div>

      {failedSkippedRows.length > 0 ? (
        <div className="space-y-2" data-testid="lab-failed-skipped-section">
          <span className="text-muted-foreground text-xs font-medium">{t("benchmarkFailedSkippedTitle")}</span>
          <LabComparisonTable maxHeightClassName="max-h-40" testId="lab-failed-skipped-table">
            <LabComparisonTableHead>
              <tr>
                <LabComparisonTh>{t("benchmarkColItem")}</LabComparisonTh>
                {benchmarkKind !== "EMBEDDING_RETRIEVAL" ? (
                  <LabComparisonTh>{t("benchmarkColPreset")}</LabComparisonTh>
                ) : null}
                <LabComparisonTh>
                  {benchmarkKind === "EMBEDDING_RETRIEVAL"
                    ? t("benchmarkColEmbeddingModel")
                    : t("benchmarkColModel")}
                </LabComparisonTh>
                <LabComparisonTh>{t("benchmarkColOutcome")}</LabComparisonTh>
                {benchmarkKind !== "EMBEDDING_RETRIEVAL" ? (
                  <LabComparisonTh>{t("benchmarkColNote")}</LabComparisonTh>
                ) : null}
              </tr>
            </LabComparisonTableHead>
            <tbody>
              {benchmarkKind === "EMBEDDING_RETRIEVAL"
                ? (failedSlice.pageRows as unknown as EmbeddingItemRow[]).map((row) => {
                    const snippet = row.question.length > 72 ? `${row.question.slice(0, 72)}…` : row.question;
                    return (
                      <tr key={`failed-${row.id}`} className="border-t border-border">
                        <td className="p-2 align-top">{snippet || "—"}</td>
                        <td className="p-2 align-top">{displayModelId(row.embeddingModelId, t)}</td>
                        <td className="p-2 align-top">{formatOutcomeLabel(row.outcome, t)}</td>
                      </tr>
                    );
                  })
                : (failedSlice.pageRows as ResultTableRow[]).map((row) => {
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
          </LabComparisonTable>
          <LabTablePaginationBar
            page={failedSlice.page}
            pageSize={failedSlice.pageSize}
            totalRows={failedSlice.totalRows}
            totalPages={failedSlice.totalPages}
            rangeStart={failedSlice.rangeStart}
            rangeEnd={failedSlice.rangeEnd}
            onPageChange={setFailedPage}
            onPageSizeChange={setFailedPageSize}
            testId="lab-failed-skipped-pagination"
          />
        </div>
      ) : null}
    </div>
  );
}
