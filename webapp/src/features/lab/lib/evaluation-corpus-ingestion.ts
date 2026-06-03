import type {
  EvaluationCorpusDocumentsUploadResponseDto,
  EvaluationCorpusSummaryDto,
  ProjectDocumentDto,
} from "@/types/api";

/** Maps multipart upload item status to persisted project document status. */
export function uploadItemStatusToDocumentStatus(
  uploadStatus: string,
): ProjectDocumentDto["status"] {
  if (uploadStatus === "READY") {
    return "READY";
  }
  if (uploadStatus === "FAILED" || uploadStatus === "ERROR") {
    return "ERROR";
  }
  if (uploadStatus === "DUPLICATE") {
    return "READY";
  }
  return "INGESTING";
}

/**
 * Merges upload response into corpus cache so new rows appear immediately (PROCESSING/INGESTING)
 * before refetch/polling returns terminal READY/ERROR.
 */
export function mergeCorpusAfterUpload(
  previous: EvaluationCorpusSummaryDto | undefined,
  upload: EvaluationCorpusDocumentsUploadResponseDto,
): EvaluationCorpusSummaryDto {
  const base = upload.corpus ?? previous;
  if (!base) {
    throw new Error("Upload response missing corpus summary");
  }

  const byId = new Map<string, ProjectDocumentDto>();
  for (const doc of base.documents ?? []) {
    if (doc.id) {
      byId.set(doc.id, doc);
    }
  }

  for (const item of upload.uploads) {
    if (item.status === "DUPLICATE" || !item.documentId) {
      continue;
    }
    const status = uploadItemStatusToDocumentStatus(item.status);
    const existing = byId.get(item.documentId);
    byId.set(item.documentId, {
      id: item.documentId,
      fileName: item.fileName ?? existing?.fileName ?? "document",
      status,
      chunkCount: existing?.chunkCount ?? null,
      errorMessage: status === "ERROR" ? (item.error ?? existing?.errorMessage ?? null) : null,
      uploadedAt: existing?.uploadedAt ?? new Date().toISOString(),
      reindexedAt: existing?.reindexedAt ?? null,
      corpusScope: existing?.corpusScope ?? "PROJECT_SHARED",
      conversationId: existing?.conversationId ?? null,
      currentIndexSnapshotId: existing?.currentIndexSnapshotId ?? null,
      indexSignatureHash: existing?.indexSignatureHash ?? null,
      storagePresent: existing?.storagePresent ?? true,
    });
  }

  const documents = Array.from(byId.values());
  const readyCount = documents.filter((d) => d.status === "READY").length;
  const failedCount = documents.filter((d) => d.status === "ERROR").length;

  return {
    ...base,
    documents,
    documentCount: documents.length,
    readyCount,
    failedCount,
  };
}

export function isNonTerminalDocumentStatus(status: string): boolean {
  return status === "INGESTING" || status === "PROCESSING";
}

export function corpusAllDocumentsTerminal(
  summary: { documents: { status: string }[] } | null | undefined,
): boolean {
  if (!summary?.documents?.length) {
    return true;
  }
  return summary.documents.every((d) => !isNonTerminalDocumentStatus(d.status));
}

/** Stable ingestion error codes → short operator-facing text (English; i18n wraps in UI). */
export function humanizeIngestionErrorMessage(raw: string | null | undefined): string | null {
  if (!raw?.trim()) {
    return null;
  }
  const msg = raw.trim();
  const code = msg.split(/[:\s]/)[0]?.toUpperCase() ?? "";

  if (code === "DUPLICATE_FILE" || msg.startsWith("DUPLICATE_FILE")) {
    return "DUPLICATE_FILE";
  }
  if (code === "UNSUPPORTED_TYPE" || msg.includes("unsupported type") || msg.includes("UNSUPPORTED")) {
    return "UNSUPPORTED_FILE";
  }
  if (msg.includes("parse") || code === "PARSE_ERROR") {
    return "PARSE_ERROR";
  }
  if (msg.includes("embed") || code === "EMBEDDING_ERROR" || msg.includes("context limit")) {
    return "EMBEDDING_ERROR";
  }
  if (msg.includes("index") || code === "INDEX_ERROR") {
    return "INDEX_ERROR";
  }
  if (msg.includes("empty") || code === "EMPTY_BYTES") {
    return "EMPTY_FILE";
  }
  if (msg.includes("timed out") || msg.includes("watchdog") || code === "FAILED_STALE_INGESTION") {
    return "INGESTION_TIMEOUT";
  }
  if (code === "FAILED_EMBEDDING") {
    return "EMBEDDING_ERROR";
  }
  if (code === "FAILED_PARSING") {
    return "PARSE_ERROR";
  }
  if (code === "FAILED_INDEX") {
    return "INDEX_ERROR";
  }
  return msg;
}
