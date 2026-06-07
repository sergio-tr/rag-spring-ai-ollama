import { ApiError } from "@/lib/api-client";

const REASON_CODE_RE = /^[A-Z][A-Z0-9_]+$/;

/** Reads stable `code` from ApiError JSON body when present. */
export function extractApiErrorCode(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null;
  }
  const parsed = error.meta?.parsedJson;
  if (parsed && typeof parsed === "object") {
    const root = parsed as Record<string, unknown>;
    if (typeof root.code === "string" && REASON_CODE_RE.test(root.code)) {
      return root.code;
    }
    const nested = root.error as Record<string, unknown> | undefined;
    if (typeof nested?.code === "string" && REASON_CODE_RE.test(nested.code)) {
      return nested.code;
    }
  }
  const head = (error.message ?? "").trim().split(/[:\s]/)[0] ?? "";
  return REASON_CODE_RE.test(head) ? head : null;
}
import { isNonTerminalDocumentStatus } from "@/features/lab/lib/evaluation-corpus-ingestion";
import { mapUserFacingErrorMessage } from "@/lib/user-facing-error-messages";
import type { EvaluationCorpusDocumentsUploadResponseDto } from "@/types/api";

export function corpusUploadErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 413) {
      return "FILE_TOO_LARGE";
    }
    if (error.status === 415) {
      return "UNSUPPORTED_TYPE";
    }
    const code = extractApiErrorCode(error);
    if (code) {
      return code;
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

export function corpusUploadDuplicates(
  response: EvaluationCorpusDocumentsUploadResponseDto,
): EvaluationCorpusDocumentsUploadResponseDto["uploads"] {
  return response.uploads.filter((u) => u.status === "DUPLICATE");
}

export function summarizeCorpusUploadDuplicates(
  response: EvaluationCorpusDocumentsUploadResponseDto,
): string | null {
  const duplicates = corpusUploadDuplicates(response);
  if (duplicates.length === 0) {
    return null;
  }
  return duplicates.map((u) => u.fileName).join(", ");
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

export function summarizeCorpusUploadFailuresForDisplay(
  response: EvaluationCorpusDocumentsUploadResponseDto,
  t: (key: string) => string,
): string | null {
  const failed = response.uploads.filter((u) => u.status === "FAILED");
  if (failed.length === 0) {
    return null;
  }
  return failed
    .map((u) =>
      `${u.fileName}: ${mapUserFacingErrorMessage(u.error, t, t("labCorpusUploadFailed"))}`,
    )
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
  return summary.documents.some((d) => isNonTerminalDocumentStatus(d.status));
}

/** Maps stable backend knowledge-base / RAG error codes to Lab i18n keys. */
export function mapKnowledgeBaseApiError(
  message: string,
  t: (key: string) => string,
  fallback: string,
): string {
  const trimmed = message?.trim() ?? "";
  if (trimmed.startsWith("DUPLICATE_FILE") || trimmed.split(/[:\s]/)[0] === "DUPLICATE_FILE") {
    return t("labKbDuplicateFile");
  }
  return mapUserFacingErrorMessage(trimmed, t, fallback);
}
