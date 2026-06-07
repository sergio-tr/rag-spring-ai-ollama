import {
  corpusHasProcessingDocuments,
  corpusHasReadyDocuments,
} from "@/features/lab/lib/evaluation-corpus-upload";
import type { EvaluationCorpusReadinessDto, EvaluationCorpusSummaryDto } from "@/types/api";

/** Snapshot blockers that auto-resolve when the evaluation run prepares indexes internally. */
const BUILDABLE_INDEX_SNAPSHOT_BLOCKERS = new Set([
  "INDEX_PREPARATION_REQUIRED",
  "REINDEX_REQUIRED",
  "NO_ACTIVE_SNAPSHOT",
  "NO_COMPATIBLE_SNAPSHOT",
  "SNAPSHOT_VECTOR_ROWS_MISSING",
  "SNAPSHOT_INCOMPATIBLE",
]);

export function isBuildableIndexSnapshotBlocker(code: string | null | undefined): boolean {
  if (!code) {
    return false;
  }
  return BUILDABLE_INDEX_SNAPSHOT_BLOCKERS.has(code);
}

export function isHardIndexSnapshotBlocker(code: string | null | undefined): boolean {
  return Boolean(code && !isBuildableIndexSnapshotBlocker(code));
}

export type DocumentCentricReadinessDisplay = {
  kind: "blocker" | "info" | "error";
  messageKey: string;
  testId: string;
  technicalCode?: string | null;
};

export function resolveDocumentCentricReadinessDisplay(
  readiness: EvaluationCorpusReadinessDto | null | undefined,
  summary: EvaluationCorpusSummaryDto | null | undefined,
): DocumentCentricReadinessDisplay | null {
  const primary = readiness?.primaryBlocker ?? null;
  const docCount = summary?.documentCount ?? readiness?.documentCount ?? 0;
  const readyCount = summary?.readyCount ?? readiness?.readyCount ?? 0;

  if (primary === "NO_DOCUMENTS" || primary === "KB_EMPTY" || docCount === 0) {
    return { kind: "blocker", messageKey: "labEvalAddDocuments", testId: "lab-corpus-readiness-blocker" };
  }
  if (
    primary === "NO_READY_DOCUMENTS" ||
    (docCount > 0 && readyCount === 0 && corpusHasProcessingDocuments(summary))
  ) {
    return {
      kind: "blocker",
      messageKey: "labEvalDocumentsProcessing",
      testId: "lab-corpus-readiness-blocker",
    };
  }
  if (primary === "DOCUMENT_PROCESSING_FAILED") {
    return {
      kind: "blocker",
      messageKey: "userError_DOCUMENT_PROCESSING_FAILED",
      testId: "lab-corpus-readiness-blocker",
    };
  }
  if (primary) {
    return {
      kind: "blocker",
      messageKey: "labCorpusReadinessBlocked",
      testId: "lab-corpus-readiness-blocker",
    };
  }

  const snapshotBlocker = readiness?.snapshotBlocker ?? null;
  if (!snapshotBlocker) {
    return null;
  }
  if (isHardIndexSnapshotBlocker(snapshotBlocker)) {
    return {
      kind: "error",
      messageKey: "labEvalIndexPrepareFailed",
      testId: "lab-corpus-index-failed",
      technicalCode: snapshotBlocker,
    };
  }
  if (!corpusHasReadyDocuments(summary) && !readiness?.runnable) {
    return null;
  }
  return {
    kind: "info",
    messageKey: "labEvalIndexWillPrepare",
    testId: "lab-corpus-index-will-prepare",
  };
}
