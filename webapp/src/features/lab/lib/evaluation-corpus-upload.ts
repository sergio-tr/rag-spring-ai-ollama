import { ApiError } from "@/lib/api-client";
import type { EvaluationCorpusDocumentsUploadResponseDto } from "@/types/api";

export function corpusUploadErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 413) {
      return "FILE_TOO_LARGE";
    }
    if (error.status === 415) {
      return "UNSUPPORTED_TYPE";
    }
    const detail = error.message?.trim();
    if (detail) {
      return detail;
    }
  }
  if (error instanceof Error && error.message.trim()) {
    return error.message.trim();
  }
  return fallback;
}

export function summarizeCorpusUploadFailures(
  response: EvaluationCorpusDocumentsUploadResponseDto,
): string | null {
  const failed = response.uploads.filter((u) => u.status === "FAILED");
  if (failed.length === 0) {
    return null;
  }
  return failed
    .map((u) => `${u.fileName}: ${u.error ?? "upload failed"}`)
    .join("; ");
}

export function corpusHasReadyDocuments(
  summary: { readyCount: number; documentCount: number } | null | undefined,
): boolean {
  return Boolean(summary && summary.documentCount > 0 && summary.readyCount > 0);
}

export function corpusHasProcessingDocuments(
  summary: { documents: { status: string }[] } | null | undefined,
): boolean {
  if (!summary?.documents?.length) {
    return false;
  }
  return summary.documents.some((d) => d.status === "INGESTING" || d.status === "PROCESSING");
}

const KB_ERROR_CODES = new Set([
  "KB_NOT_FOUND",
  "KB_EMPTY",
  "NO_READY_DOCUMENTS",
  "DOCUMENT_PROCESSING_FAILED",
  "NO_CORPUS_SELECTED",
  "NO_DOCUMENTS",
  "REINDEX_FAILED",
  "SNAPSHOT_PREPARATION_FAILED",
]);

/** Maps stable backend knowledge-base / RAG error codes to Lab i18n keys. */
export function mapKnowledgeBaseApiError(
  message: string,
  t: (key: string) => string,
  fallback: string,
): string {
  const code = message?.trim().split(/[:\s]/)[0] ?? "";
  if (!KB_ERROR_CODES.has(code)) {
    return message?.trim() || fallback;
  }
  switch (code) {
    case "KB_NOT_FOUND":
      return t("labKbNotFound");
    case "KB_EMPTY":
    case "NO_DOCUMENTS":
      return t("labKbEmpty");
    case "NO_READY_DOCUMENTS":
      return t("labKbNoReadyDocuments");
    case "DOCUMENT_PROCESSING_FAILED":
      return t("labKbDocumentProcessingFailed");
    case "NO_CORPUS_SELECTED":
      return t("benchmarkNeedsCorpus");
    case "REINDEX_FAILED":
    case "SNAPSHOT_PREPARATION_FAILED":
      return t("labRagSnapshotPreparationFailed");
    default:
      return fallback;
  }
}
