import type { ComparisonRow } from "@/features/lab/lib/lab-benchmark-labels";
import {
  formatPresetDisplay,
  formatOutcomeLabel,
  isExtensionPreset,
  isMissingMetadata,
  resolveComparisonRowLabel,
  resolvePresetKeyFromComparisonRow,
} from "@/features/lab/lib/lab-benchmark-labels";
import { mapBenchmarkSkipReason } from "@/features/lab/lib/lab-benchmark-skip-reasons";
import { readDerivedErrorClassFromItem } from "@/features/lab/lib/lab-benchmark-mvp-utils";
import { mapUserFacingErrorMessage } from "@/lib/user-facing-error-messages";
import { sortRowsByKey, type TableSortState } from "@/features/lab/lib/lab-table-sort";
import type { EmbeddingItemRow } from "@/features/lab/lib/embedding-result-table";

export type ResultTableRow = {
  id: string;
  question: string;
  expectedAnswer: string;
  actualAnswer: string;
  contextText: string;
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

function firstNonBlank(...values: Array<string | null | undefined>): string {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return "";
}

export function toResultTableRow(row: unknown, idx: number, t: (key: string) => string): ResultTableRow {
  const item = asRecord(row);
  const mvp = asRecord(item?.mvp);
  const generation = asRecord(mvp?.generation);
  const analysis = asRecord(mvp?.analysis);
  const op = asRecord(mvp?.operational);
  const outcomeFromStatus = typeof item?.status === "string" && item.status.trim() ? item.status.trim() : "";
  const outcome = typeof op?.outcome === "string" && op.outcome ? op.outcome : outcomeFromStatus || "-";
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
  const presetCode = rawPreset && !isMissingMetadata(rawPreset) ? rawPreset : "-";
  const rawModelTop =
    typeof item?.embeddingModelId === "string" && item.embeddingModelId.trim()
      ? item.embeddingModelId.trim()
      : typeof item?.llmModelId === "string" && item.llmModelId.trim()
        ? item.llmModelId.trim()
        : typeof item?.modelLabel === "string"
          ? item.modelLabel.trim()
          : "";
  const rawModel = typeof op?.modelId === "string" && op.modelId.trim() ? op.modelId.trim() : rawModelTop;
  const modelId = rawModel && !isMissingMetadata(rawModel) ? rawModel : "-";
  const presetLabelRaw = typeof item?.presetLabel === "string" ? item.presetLabel : "";
  const mp = asRecord(item?.metricsPayload);
  const presetLabelFromPayload = typeof mp?.presetLabel === "string" ? mp.presetLabel : presetLabelRaw;
  const presetLabel = presetCode !== "-" ? formatPresetDisplay(presetCode, presetLabelFromPayload) : "-";
  const question =
    typeof item?.questionText === "string"
      ? item.questionText
      : typeof item?.question === "string"
        ? item.question
        : "";
  const expectedAnswer = firstNonBlank(
    typeof item?.expectedAnswer === "string" ? item.expectedAnswer : null,
    typeof mp?.expected_answer === "string" ? mp.expected_answer : null,
    typeof generation?.expectedAnswer === "string" ? generation.expectedAnswer : null,
  );
  const actualAnswer = firstNonBlank(
    typeof item?.actualAnswer === "string" ? item.actualAnswer : null,
    typeof item?.answer === "string" ? item.answer : null,
    typeof generation?.actualAnswer === "string" ? generation.actualAnswer : null,
  );
  const contextText = firstNonBlank(
    typeof item?.contextText === "string" ? item.contextText : null,
    typeof item?.context_text === "string" ? item.context_text : null,
    typeof mp?.context_text === "string" ? mp.context_text : null,
    typeof mp?.contextText === "string" ? mp.contextText : null,
    typeof analysis?.contextText === "string" ? analysis.contextText : null,
    typeof analysis?.context_text === "string" ? analysis.context_text : null,
  );
  const snapshotId =
    typeof item?.snapshotId === "string" && item.snapshotId.trim()
      ? item.snapshotId.trim()
      : typeof mp?.indexSnapshotId === "string"
        ? mp.indexSnapshotId
        : "-";
  const sourcesRaw = item?.sources;
  let sourcesSummary = "-";
  if (Array.isArray(sourcesRaw) && sourcesRaw.length > 0) {
    sourcesSummary = `${sourcesRaw.length} source(s)`;
  } else if (typeof mp?.retrieved_document_ids === "string" && mp.retrieved_document_ids.trim()) {
    sourcesSummary = `${mp.retrieved_document_ids.split(";").filter(Boolean).length} doc(s)`;
  } else if (typeof mp?.retrieved_chunk_ids === "string" && mp.retrieved_chunk_ids.trim()) {
    sourcesSummary = `${mp.retrieved_chunk_ids.split(";").filter(Boolean).length} chunk(s)`;
  } else if (Array.isArray(mp?.retrieved_chunk_ids) && mp.retrieved_chunk_ids.length > 0) {
    sourcesSummary = `${mp.retrieved_chunk_ids.length} chunk(s)`;
  } else if (Array.isArray(mp?.retrieved_document_ids) && mp.retrieved_document_ids.length > 0) {
    sourcesSummary = `${mp.retrieved_document_ids.length} doc(s)`;
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
        ? mapUserFacingErrorMessage(unsupportedReason, t, formatOutcomeLabel("NOT_SUPPORTED", t))
        : outcome === "SKIPPED"
          ? skipMapped.primary
          : outcome === "FAILED"
            ? t("benchmarkNoteSeeExport")
            : "-";
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
    expectedAnswer,
    actualAnswer,
    contextText,
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

export function sortResultTableRows(rows: ResultTableRow[], sort: TableSortState): ResultTableRow[] {
  const defaultSorted = [...rows].sort((a, b) => {
    const av = a.correctness ?? -1;
    const bv = b.correctness ?? -1;
    return bv - av;
  });
  if (!sort) return defaultSorted;
  return sortRowsByKey(defaultSorted, sort, (row, key) => {
    switch (key) {
      case "question":
        return row.question;
      case "preset":
        return row.presetLabel;
      case "model":
        return row.modelId;
      case "expectedAnswer":
        return row.expectedAnswer;
      case "actualAnswer":
        return row.actualAnswer;
      case "context":
        return row.contextText;
      case "sources":
        return row.sourcesSummary;
      case "outcome":
        return row.outcome;
      case "correctness":
        return row.correctness;
      case "llmJudgeScore":
        return row.llmJudgeScore;
      case "hallucinationRate":
        return row.hallucinationRate;
      case "dateCorrectness":
        return row.dateCorrectness;
      case "note":
        return row.note;
      default:
        return null;
    }
  });
}

export function sortEmbeddingPerItemRows(rows: EmbeddingItemRow[], sort: TableSortState): EmbeddingItemRow[] {
  const defaultSorted = [...rows].sort((a, b) => {
    const av = a.hitAt1 ?? -1;
    const bv = b.hitAt1 ?? -1;
    if (bv !== av) return bv - av;
    const ar = a.goldRank ?? Number.MAX_SAFE_INTEGER;
    const br = b.goldRank ?? Number.MAX_SAFE_INTEGER;
    return ar - br;
  });
  if (!sort) return defaultSorted;
  return sortRowsByKey(defaultSorted, sort, (row, key) => {
    switch (key) {
      case "question":
        return row.question;
      case "model":
        return row.embeddingModelId;
      case "expectedGold":
        return row.expectedGold;
      case "retrievedTop1":
        return row.retrievedTop1;
      case "goldRank":
        return row.goldRank;
      case "hitAt1":
        return row.hitAt1;
      case "hitAt3":
        return row.hitAt3;
      case "hitAt5":
        return row.hitAt5;
      case "topScore":
        return row.topScore;
      case "latencyMs":
        return row.latencyMs;
      case "outcome":
        return row.outcome;
      default:
        return null;
    }
  });
}

export function sortComparisonRowsByKey(rows: ComparisonRow[], sort: TableSortState, comparisonAxis: string): ComparisonRow[] {
  const defaultSorted = [...rows].sort((a, b) => {
    const av = typeof a.meanExactMatch === "number" ? a.meanExactMatch : typeof a.meanCorrectness === "number" ? a.meanCorrectness : -1;
    const bv = typeof b.meanExactMatch === "number" ? b.meanExactMatch : typeof b.meanCorrectness === "number" ? b.meanCorrectness : -1;
    return bv - av;
  });
  if (!sort) return defaultSorted;
  return sortRowsByKey(defaultSorted, sort, (row, key) => comparisonRowSortValue(row, key, comparisonAxis));
}

function comparisonRowSortValue(row: ComparisonRow, key: string, comparisonAxis: string): unknown {
  switch (key) {
    case "model":
    case "llmModel":
      return row.llmModelId ?? resolveComparisonRowLabel(row, comparisonAxis);
    case "embeddingModel":
      return row.embeddingModelId ?? resolveComparisonRowLabel(row, comparisonAxis);
    case "preset":
      return resolvePresetKeyFromComparisonRow(row) || resolveComparisonRowLabel(row, comparisonAxis);
    case "group":
      return row.groupValue;
    case "correctness":
      return row.meanCorrectness ?? row.meanExactMatch;
    case "faithfulness":
      return row.meanFaithfulness;
    case "containsExpected":
      return row.containsExpectedAnswerRate;
    case "hallucinationRate":
      return row.meanHallucinationRate;
    case "emptyContent":
      return row.emptyContentCount;
    case "timeout":
      return row.timeoutCount;
    case "meanLatency":
      return row.meanLatencyMs;
    case "p95Latency":
      return row.p95LatencyMs;
    case "errors":
      return (row.failed ?? 0) + (row.errorCount ?? 0);
    case "executed":
      return row.executed;
    case "planned":
      return row.totalItems;
    case "noContext":
      return row.noContextCount;
    case "coverage":
      return row.executed != null && row.totalItems ? row.executed / row.totalItems : null;
    case "globalScore":
      return row.scoreGlobal;
    case "recallAt1":
      return row.meanRecallAt1;
    case "recallAt3":
      return row.meanRecallAt3;
    case "recallAt5":
      return row.meanRecallAt5;
    case "mrr":
      return row.meanMrr;
    case "ndcgAt5":
      return row.meanNdcgAt5;
    default:
      return null;
  }
}

export function truncateTableCell(value: string, max = 64): { display: string; full: string } {
  const full = value.trim();
  if (!full) return { display: "-", full: "" };
  if (full.length <= max) return { display: full, full };
  return { display: `${full.slice(0, max)}…`, full };
}
