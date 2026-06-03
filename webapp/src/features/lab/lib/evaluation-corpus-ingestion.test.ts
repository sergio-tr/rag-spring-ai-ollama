import { describe, expect, it } from "vitest";
import {
  corpusAllDocumentsTerminal,
  humanizeIngestionErrorMessage,
  mergeCorpusAfterUpload,
  uploadItemStatusToDocumentStatus,
} from "./evaluation-corpus-ingestion";
import type { EvaluationCorpusSummaryDto } from "@/types/api";

const baseCorpus: EvaluationCorpusSummaryDto = {
  id: "corpus-1",
  name: "KB",
  sourceType: "UPLOADED",
  documentCount: 0,
  readyCount: 0,
  failedCount: 0,
  documents: [],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("evaluation-corpus-ingestion", () => {
  it("maps upload statuses to document statuses", () => {
    expect(uploadItemStatusToDocumentStatus("PROCESSING")).toBe("INGESTING");
    expect(uploadItemStatusToDocumentStatus("READY")).toBe("READY");
    expect(uploadItemStatusToDocumentStatus("FAILED")).toBe("ERROR");
  });

  it("merges new upload rows into corpus cache immediately", () => {
    const merged = mergeCorpusAfterUpload(baseCorpus, {
      corpus: baseCorpus,
      uploads: [{ documentId: "d1", fileName: "a.txt", status: "PROCESSING", error: null }],
    });
    expect(merged.documentCount).toBe(1);
    expect(merged.documents[0]?.status).toBe("INGESTING");
    expect(merged.readyCount).toBe(0);
  });

  it("detects terminal vs non-terminal documents", () => {
    expect(
      corpusAllDocumentsTerminal({
        documents: [{ status: "READY" }, { status: "ERROR" }],
      }),
    ).toBe(true);
    expect(
      corpusAllDocumentsTerminal({
        documents: [{ status: "READY" }, { status: "INGESTING" }],
      }),
    ).toBe(false);
  });

  it("humanizes ingestion errors to stable codes", () => {
    expect(humanizeIngestionErrorMessage("unsupported type for file")).toBe("UNSUPPORTED_FILE");
    expect(humanizeIngestionErrorMessage("Document indexing failed")).toBe("INDEX_ERROR");
  });
});
