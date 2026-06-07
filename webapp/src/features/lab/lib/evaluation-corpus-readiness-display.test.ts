import { describe, expect, it } from "vitest";
import {
  isBuildableIndexSnapshotBlocker,
  isHardIndexSnapshotBlocker,
  resolveDocumentCentricReadinessDisplay,
} from "./evaluation-corpus-readiness-display";
import type { EvaluationCorpusReadinessDto, EvaluationCorpusSummaryDto } from "@/types/api";

function readiness(
  overrides: Partial<EvaluationCorpusReadinessDto> = {},
): EvaluationCorpusReadinessDto {
  return {
    corpusId: "c1",
    indexProjectId: "p1",
    documentCount: 1,
    readyCount: 1,
    processingCount: 0,
    failedCount: 0,
    primaryBlocker: null,
    primaryBlockerMessage: null,
    activeSnapshotId: null,
    reindexRequired: true,
    snapshotBlocker: "INDEX_PREPARATION_REQUIRED",
    snapshotBlockerDetailCode: null,
    selectedSnapshotIds: [],
    runnable: true,
    ...overrides,
  };
}

function summary(
  overrides: Partial<EvaluationCorpusSummaryDto> = {},
): EvaluationCorpusSummaryDto {
  return {
    id: "c1",
    name: "Lab knowledge base",
    sourceType: "UPLOADED",
    documentCount: 1,
    readyCount: 1,
    failedCount: 0,
    documents: [{ id: "d1", fileName: "a.pdf", status: "READY", errorMessage: null }],
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("evaluation-corpus-readiness-display", () => {
  it("maps missing documents to add-documents blocker", () => {
    const display = resolveDocumentCentricReadinessDisplay(
      readiness({ primaryBlocker: "NO_DOCUMENTS", runnable: false, documentCount: 0, readyCount: 0 }),
      summary({ documentCount: 0, readyCount: 0, documents: [] }),
    );
    expect(display).toEqual({
      kind: "blocker",
      messageKey: "labEvalAddDocuments",
      testId: "lab-corpus-readiness-blocker",
    });
  });

  it("maps processing documents to processing blocker", () => {
    const display = resolveDocumentCentricReadinessDisplay(
      readiness({
        primaryBlocker: "NO_READY_DOCUMENTS",
        runnable: false,
        readyCount: 0,
        processingCount: 1,
      }),
      summary({
        readyCount: 0,
        documents: [{ id: "d1", fileName: "a.pdf", status: "INGESTING", errorMessage: null }],
      }),
    );
    expect(display?.messageKey).toBe("labEvalDocumentsProcessing");
  });

  it("treats index preparation as informational when runnable", () => {
    const display = resolveDocumentCentricReadinessDisplay(
      readiness({ snapshotBlocker: "INDEX_PREPARATION_REQUIRED" }),
      summary(),
    );
    expect(display).toEqual({
      kind: "info",
      messageKey: "labEvalIndexWillPrepare",
      testId: "lab-corpus-index-will-prepare",
    });
  });

  it("classifies buildable snapshot blockers", () => {
    expect(isBuildableIndexSnapshotBlocker("REINDEX_REQUIRED")).toBe(true);
    expect(isBuildableIndexSnapshotBlocker("INDEX_PREPARATION_REQUIRED")).toBe(true);
    expect(isHardIndexSnapshotBlocker("REINDEX_FAILED")).toBe(true);
  });
});
