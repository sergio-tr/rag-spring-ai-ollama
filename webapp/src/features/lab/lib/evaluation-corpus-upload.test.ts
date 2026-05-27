import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api-client";
import type { EvaluationCorpusDocumentsUploadResponseDto } from "@/types/api";
import {
  corpusHasProcessingDocuments,
  corpusHasReadyDocuments,
  corpusUploadErrorMessage,
  mapKnowledgeBaseApiError,
  summarizeCorpusUploadFailures,
} from "./evaluation-corpus-upload";

describe("evaluation-corpus-upload", () => {
  it("maps 413 to FILE_TOO_LARGE", () => {
    expect(corpusUploadErrorMessage(new ApiError(413, "too big"), "fallback")).toBe("FILE_TOO_LARGE");
  });

  it("maps 415 to UNSUPPORTED_TYPE", () => {
    expect(corpusUploadErrorMessage(new ApiError(415, "bad type"), "fallback")).toBe("UNSUPPORTED_TYPE");
  });

  it("uses ApiError message when present", () => {
    expect(corpusUploadErrorMessage(new ApiError(400, "  detail  "), "fallback")).toBe("detail");
  });

  it("falls back when ApiError has no message", () => {
    expect(corpusUploadErrorMessage(new ApiError(500, "   "), "fallback")).toBe("fallback");
  });

  it("uses generic Error message", () => {
    expect(corpusUploadErrorMessage(new Error(" network "), "fallback")).toBe("network");
  });

  it("returns fallback for unknown errors", () => {
    expect(corpusUploadErrorMessage({ code: "x" }, "fallback")).toBe("fallback");
  });

  it("returns null when no upload failures", () => {
    const response: EvaluationCorpusDocumentsUploadResponseDto = {
      corpus: {
        id: "c1",
        name: "x",
        sourceType: "UPLOADED",
        documentCount: 1,
        readyCount: 1,
        failedCount: 0,
        documents: [],
        createdAt: "",
        updatedAt: "",
      },
      uploads: [{ documentId: "d1", fileName: "ok.pdf", status: "READY", error: null }],
    };
    expect(summarizeCorpusUploadFailures(response)).toBeNull();
  });

  it("summarizes per-file failures", () => {
    const response: EvaluationCorpusDocumentsUploadResponseDto = {
      corpus: {
        id: "c1",
        name: "x",
        sourceType: "UPLOADED",
        documentCount: 1,
        readyCount: 0,
        failedCount: 1,
        documents: [],
        createdAt: "",
        updatedAt: "",
      },
      uploads: [
        { documentId: null, fileName: "bad.pdf", status: "FAILED", error: "unsupported" },
      ],
    };
    expect(summarizeCorpusUploadFailures(response)).toContain("bad.pdf");
  });

  it("detects ready corpus", () => {
    expect(corpusHasReadyDocuments({ documentCount: 2, readyCount: 1 })).toBe(true);
    expect(corpusHasReadyDocuments({ documentCount: 1, readyCount: 0 })).toBe(false);
    expect(corpusHasReadyDocuments(null)).toBe(false);
    expect(corpusHasReadyDocuments({ documentCount: 0, readyCount: 0 })).toBe(false);
  });

  it("detects processing documents", () => {
    expect(
      corpusHasProcessingDocuments({
        documents: [{ status: "READY" }, { status: "INGESTING" }],
      }),
    ).toBe(true);
    expect(
      corpusHasProcessingDocuments({
        documents: [{ status: "READY" }, { status: "PROCESSING" }],
      }),
    ).toBe(true);
    expect(
      corpusHasProcessingDocuments({
        documents: [{ status: "READY" }],
      }),
    ).toBe(false);
    expect(corpusHasProcessingDocuments(null)).toBe(false);
    expect(corpusHasProcessingDocuments({ documents: [] })).toBe(false);
  });

  it("mapKnowledgeBaseApiError maps known KB codes to i18n keys", () => {
    const t = (key: string) => `i18n:${key}`;
    expect(mapKnowledgeBaseApiError("KB_NOT_FOUND", t, "fb")).toBe("i18n:labKbNotFound");
    expect(mapKnowledgeBaseApiError("KB_EMPTY", t, "fb")).toBe("i18n:labKbEmpty");
    expect(mapKnowledgeBaseApiError("NO_DOCUMENTS", t, "fb")).toBe("i18n:labKbEmpty");
    expect(mapKnowledgeBaseApiError("NO_READY_DOCUMENTS", t, "fb")).toBe("i18n:labKbNoReadyDocuments");
    expect(mapKnowledgeBaseApiError("DOCUMENT_PROCESSING_FAILED", t, "fb")).toBe(
      "i18n:labKbDocumentProcessingFailed",
    );
    expect(mapKnowledgeBaseApiError("NO_CORPUS_SELECTED", t, "fb")).toBe("i18n:benchmarkNeedsCorpus");
    expect(mapKnowledgeBaseApiError("REINDEX_FAILED", t, "fb")).toBe("i18n:labRagSnapshotPreparationFailed");
    expect(mapKnowledgeBaseApiError("SNAPSHOT_PREPARATION_FAILED", t, "fb")).toBe(
      "i18n:labRagSnapshotPreparationFailed",
    );
  });

  it("mapKnowledgeBaseApiError returns raw message for unknown codes", () => {
    const t = (key: string) => key;
    expect(mapKnowledgeBaseApiError("CUSTOM detail", t, "fb")).toBe("CUSTOM detail");
    expect(mapKnowledgeBaseApiError("  ", t, "fb")).toBe("fb");
  });

  it("uses default message when failed upload has no error", () => {
    const response: EvaluationCorpusDocumentsUploadResponseDto = {
      corpus: {
        id: "c1",
        name: "x",
        sourceType: "UPLOADED",
        documentCount: 0,
        readyCount: 0,
        failedCount: 1,
        documents: [],
        createdAt: "",
        updatedAt: "",
      },
      uploads: [{ documentId: null, fileName: "x.pdf", status: "FAILED", error: null }],
    };
    expect(summarizeCorpusUploadFailures(response)).toBe("x.pdf: upload failed");
  });
});
