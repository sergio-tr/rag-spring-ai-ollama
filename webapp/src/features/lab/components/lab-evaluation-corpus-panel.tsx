"use client";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  useEvaluationCorpus,
  type EvaluationCorpusApi,
} from "@/features/lab/hooks/use-evaluation-corpus";
import { humanizeIngestionErrorMessage } from "@/features/lab/lib/evaluation-corpus-ingestion";
import {
  corpusUploadErrorMessage,
  mapKnowledgeBaseApiError,
  summarizeCorpusUploadDuplicates,
  summarizeCorpusUploadFailuresForDisplay,
} from "@/features/lab/lib/evaluation-corpus-upload";
import { mapUserFacingErrorMessage } from "@/lib/user-facing-error-messages";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ProjectDocumentDto } from "@/types/api";
import { useTranslations } from "next-intl";
import { useQuery } from "@tanstack/react-query";
import { useCallback, useMemo, useRef, useState } from "react";

function isAttachableProjectDocument(
  doc: Pick<ProjectDocumentDto, "status"> & { corpusScope?: ProjectDocumentDto["corpusScope"] | null },
): boolean {
  return (
    doc.status === "READY" &&
    (doc.corpusScope == null || doc.corpusScope === "PROJECT_SHARED")
  );
}

export type LabEvaluationCorpusPanelProps = {
  corpusId: string | null;
  onCorpusIdChange: (corpusId: string | null) => void;
  /** Optional project to reuse documents from (not required). */
  optionalProjectId?: string | null;
  disabled?: boolean;
  /** Shared hook instance from {@link LabEvaluationRunCard} so upload state and Evaluate gating stay in sync. */
  evaluationCorpus?: EvaluationCorpusApi;
};

function mapIngestionErrorForDisplay(code: string | null | undefined, t: (key: string) => string): string {
  if (!code) {
    return t("labCorpusStatusFailed");
  }
  switch (code) {
    case "UNSUPPORTED_FILE":
      return t("labIngestUnsupportedFile");
    case "PARSE_ERROR":
      return t("labIngestParseError");
    case "EMBEDDING_ERROR":
      return t("labIngestEmbeddingError");
    case "INDEX_ERROR":
      return t("labIngestIndexError");
    case "EMPTY_FILE":
      return t("labIngestEmptyFile");
    case "INGESTION_TIMEOUT":
      return t("labIngestTimeout");
    case "DUPLICATE_FILE":
      return t("labKbDuplicateFile");
    default:
      return mapUserFacingErrorMessage(code, t, t("labCorpusStatusFailed"));
  }
}

function formatDocumentStatus(
  status: string,
  t: (key: string) => string,
  errorMessage?: string | null,
): string {
  if (status === "INGESTING" || status === "PROCESSING") {
    return t("labCorpusStatusProcessing");
  }
  if (status === "ERROR" || status === "FAILED") {
    const code = humanizeIngestionErrorMessage(errorMessage);
    const detail = mapIngestionErrorForDisplay(code, t);
    return `${t("labCorpusStatusFailed")} (${detail})`;
  }
  if (status === "READY") {
    return t("labCorpusStatusReady");
  }
  return status;
}

function isFailedDocumentStatus(status: string): boolean {
  return status === "ERROR" || status === "FAILED";
}

export function LabEvaluationCorpusPanel({
  corpusId,
  onCorpusIdChange,
  optionalProjectId,
  disabled = false,
  evaluationCorpus: evaluationCorpusProp,
}: LabEvaluationCorpusPanelProps) {
  const t = useTranslations("Lab");
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<string | null>(null);
  const [localErr, setLocalErr] = useState<string | null>(null);
  const [localWarn, setLocalWarn] = useState<string | null>(null);
  const [preparingIndex, setPreparingIndex] = useState(false);
  const internalCorpus = useEvaluationCorpus(evaluationCorpusProp ? null : corpusId);
  const corpusApi = evaluationCorpusProp ?? internalCorpus;
  const {
    summary,
    readiness,
    effectiveCorpusId,
    loading,
    error,
    ensureCorpus,
    uploadDocuments,
    attachFromProject,
    deleteDocument,
    deleteAllDocuments,
    retryDocumentIngest,
    refresh,
    prepareIndex,
  } = corpusApi;

  const mapUploadError = useCallback(
    (message: string) => mapKnowledgeBaseApiError(message, t, t("labCorpusUploadFailed")),
    [t],
  );

  const ensureReady = useCallback(async () => {
    const id = corpusId ?? effectiveCorpusId;
    if (id) return id;
    const created = await ensureCorpus();
    onCorpusIdChange(created.id);
    return created.id;
  }, [corpusId, effectiveCorpusId, ensureCorpus, onCorpusIdChange]);

  const refreshCorpusId = corpusId ?? effectiveCorpusId;

  const isIndexProject = Boolean(optionalProjectId && optionalProjectId === readiness?.indexProjectId);
  const attachableDocsQuery = useQuery({
    queryKey: [
      "lab",
      "corpus-attachable-project-docs",
      optionalProjectId,
      readiness?.indexProjectId,
      summary?.updatedAt,
      summary?.documentCount,
    ],
    enabled: Boolean(optionalProjectId) && !isIndexProject,
    queryFn: async () => {
      const docs = await apiFetch<ProjectDocumentDto[]>(
        apiProductPath(`/projects/${optionalProjectId}/documents`),
      );
      return docs.filter(isAttachableProjectDocument).length;
    },
    staleTime: 30_000,
  });
  const attachableProjectDocCount = !optionalProjectId
    ? null
    : isIndexProject
      ? 0
      : attachableDocsQuery.isError
        ? null
        : (attachableDocsQuery.data ?? null);

  async function onUploadSelected(files: FileList | null | undefined) {
    const list = files ? Array.from(files).filter((f) => f.size > 0) : [];
    if (list.length === 0 || disabled) return;
    setBusy(true);
    setLocalErr(null);
    setLocalWarn(null);
    setUploadProgress(t("labCorpusUploadProgress", { current: 0, total: list.length }));
    try {
      const id = await ensureReady();
      const { response } = await uploadDocuments(id, list, (current, total) => {
        setUploadProgress(t("labCorpusUploadProgress", { current, total }));
      });
      const duplicateNames = summarizeCorpusUploadDuplicates(response);
      if (duplicateNames) {
        setLocalWarn(t("labCorpusDuplicateWarning", { files: duplicateNames }));
      }
      const partial = summarizeCorpusUploadFailuresForDisplay(response, t);
      if (partial) {
        setLocalErr(t("labCorpusUploadPartialFailed", { details: partial }));
      }
    } catch (e) {
      const raw = corpusUploadErrorMessage(e, t("labCorpusUploadFailed"));
      setLocalErr(mapUploadError(raw));
    } finally {
      setBusy(false);
      setUploadProgress(null);
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function onDeleteDocument(documentId: string) {
    if (disabled) return;
    setBusy(true);
    setLocalErr(null);
    setLocalWarn(null);
    try {
      const id = await ensureReady();
      await deleteDocument(id, documentId);
    } catch (e) {
      const raw = corpusUploadErrorMessage(e, t("labCorpusDeleteFailed"));
      setLocalErr(mapUploadError(raw));
    } finally {
      setBusy(false);
    }
  }

  async function onRetryDocument(documentId: string) {
    if (disabled) return;
    setBusy(true);
    setLocalErr(null);
    setLocalWarn(null);
    try {
      const id = await ensureReady();
      await retryDocumentIngest(id, documentId);
    } catch (e) {
      const raw = corpusUploadErrorMessage(e, t("labCorpusRetryFailed"));
      setLocalErr(mapUploadError(raw));
    } finally {
      setBusy(false);
    }
  }

  async function onRefresh() {
    const id = refreshCorpusId;
    if (!id || disabled) return;
    setBusy(true);
    setLocalErr(null);
    try {
      await refresh(id);
    } catch (e) {
      const raw = corpusUploadErrorMessage(e, t("labCorpusRefreshFailed"));
      setLocalErr(mapUploadError(raw));
    } finally {
      setBusy(false);
    }
  }

  async function onPrepareIndex() {
    const id = refreshCorpusId;
    if (!id || disabled || preparingIndex) return;
    setPreparingIndex(true);
    setLocalErr(null);
    try {
      await prepareIndex(id);
    } catch (e) {
      const raw = corpusUploadErrorMessage(e, t("labCorpusPrepareIndexFailed"));
      setLocalErr(mapUploadError(raw));
    } finally {
      setPreparingIndex(false);
    }
  }

  async function onClearAll() {
    if (disabled || (summary?.documentCount ?? 0) === 0) return;
    if (!window.confirm(t("labCorpusClearAllConfirm"))) {
      return;
    }
    setBusy(true);
    setLocalErr(null);
    setLocalWarn(null);
    try {
      const id = await ensureReady();
      await deleteAllDocuments(id);
    } catch (e) {
      const raw = corpusUploadErrorMessage(e, t("labCorpusClearAllFailed"));
      setLocalErr(mapUploadError(raw));
    } finally {
      setBusy(false);
    }
  }

  async function attachAllFromOptionalProject() {
    if (!optionalProjectId || disabled) return;
    setBusy(true);
    setLocalErr(null);
    setLocalWarn(null);
    try {
      const id = await ensureReady();
      const docs = await apiFetch<ProjectDocumentDto[]>(
        apiProductPath(`/projects/${optionalProjectId}/documents`),
      );
      const sharedIds = docs.filter(isAttachableProjectDocument).map((d) => d.id);
      if (sharedIds.length === 0) {
        setLocalErr(t("labCorpusAttachNoSharedDocuments"));
        return;
      }
      await attachFromProject(id, optionalProjectId, sharedIds);
    } catch (e) {
      const raw = corpusUploadErrorMessage(e, t("labCorpusAttachFailed"));
      setLocalErr(mapUploadError(raw));
    } finally {
      setBusy(false);
    }
  }

  const displayErr = useMemo(() => {
    const raw = localErr ?? error;
    if (!raw) return null;
    return mapKnowledgeBaseApiError(raw, t, t("labCorpusUploadFailed"));
  }, [localErr, error, t]);
  const docCount = summary?.documentCount ?? 0;
  const readyCount = summary?.readyCount ?? 0;
  const showPrepareIndex =
    Boolean(readiness?.reindexRequired) &&
    !readiness?.activeSnapshotId &&
    !readiness?.primaryBlocker &&
    readyCount > 0;
  const showAttachFromProject =
    Boolean(optionalProjectId) &&
    optionalProjectId !== readiness?.indexProjectId &&
    attachableProjectDocCount !== 0;
  const showAttachUnavailableHint =
    Boolean(optionalProjectId) &&
    optionalProjectId !== readiness?.indexProjectId &&
    attachableProjectDocCount === 0;
  const showIndexProjectAttachHint =
    Boolean(optionalProjectId) && optionalProjectId === readiness?.indexProjectId;

  return (
    <div
      className="space-y-3 rounded-md border bg-muted/20 p-3 text-sm"
      data-testid="lab-evaluation-corpus-panel"
      data-lab-knowledge-base-panel=""
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-medium text-foreground">{t("labCorpusTitle")}</p>
          <details className="text-muted-foreground text-xs">
            <summary className="cursor-pointer">{t("labCorpusHelpSummary")}</summary>
            <p className="mt-1 leading-relaxed">{t("labCorpusHelp")}</p>
          </details>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="sm"
            variant="outline"
            data-testid="lab-corpus-refresh"
            disabled={disabled || busy || loading || !refreshCorpusId}
            onClick={() => void onRefresh()}
          >
            {t("labCorpusRefresh")}
          </Button>
          <Button
            type="button"
            size="sm"
            variant="outline"
            data-testid="lab-corpus-clear-all"
            disabled={disabled || busy || loading || docCount === 0}
            onClick={() => void onClearAll()}
          >
            {t("labCorpusClearAll")}
          </Button>
        </div>
      </div>

      <p className="text-muted-foreground text-xs" data-testid="lab-corpus-summary">
        {t("labCorpusSelectedSummary", { total: docCount, ready: readyCount })}
      </p>

      {readiness?.primaryBlocker ? (
        <output
          role="status"
          className="block text-destructive text-xs"
          data-testid="lab-corpus-readiness-blocker"
        >
          {t("labCorpusReadinessBlocked", {
            reason: mapKnowledgeBaseApiError(
              readiness.primaryBlocker,
              t,
              readiness.primaryBlockerMessage ?? "",
            ),
          })}
        </output>
      ) : null}

      {readiness?.snapshotBlocker && !readiness.primaryBlocker ? (
        <div className="flex flex-wrap items-center gap-2">
          <output
            role="status"
            className="block text-amber-700 dark:text-amber-400 text-xs"
            data-testid="lab-corpus-snapshot-hint"
          >
            {mapKnowledgeBaseApiError(
              readiness.snapshotBlocker,
              t,
              t("userError_REINDEX_REQUIRED"),
            )}
          </output>
          {showPrepareIndex ? (
            <Button
              type="button"
              size="sm"
              variant="secondary"
              data-testid="lab-corpus-prepare-index"
              disabled={disabled || busy || loading || preparingIndex}
              onClick={() => void onPrepareIndex()}
            >
              {preparingIndex ? t("labCorpusPrepareIndexInProgress") : t("labCorpusPrepareIndex")}
            </Button>
          ) : null}
        </div>
      ) : null}

      {!optionalProjectId ? (
        <p className="text-muted-foreground text-xs" data-testid="lab-corpus-import-hint">
          {t("labCorpusImportHintNoProject")}
        </p>
      ) : null}

      {summary?.documents && summary.documents.length > 0 ? (
        <ul className="text-muted-foreground space-y-1 text-xs" data-testid="lab-corpus-document-list">
          {summary.documents.map((d) => (
            <li
              key={d.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded border bg-background/50 px-2 py-1"
              data-testid={`lab-corpus-document-${d.id}`}
            >
              <span className="min-w-0 truncate">
                {d.fileName} —{" "}
                <span
                  data-testid={`lab-corpus-doc-status-${d.id}`}
                  data-ingestion-state={d.status}
                >
                  {formatDocumentStatus(d.status, t, d.errorMessage)}
                </span>
              </span>
              <span className="flex shrink-0 gap-1">
                {isFailedDocumentStatus(d.status) ? (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="h-7 text-xs"
                    data-testid={`lab-corpus-retry-${d.id}`}
                    disabled={disabled || busy || loading}
                    onClick={() => void onRetryDocument(d.id)}
                  >
                    {t("labCorpusRetryIngest")}
                  </Button>
                ) : null}
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  className="h-7 text-xs"
                  data-testid={`lab-corpus-delete-${d.id}`}
                  disabled={disabled || busy || loading}
                  onClick={() => void onDeleteDocument(d.id)}
                >
                  {t("labCorpusDeleteDocument")}
                </Button>
              </span>
            </li>
          ))}
        </ul>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <div>
          <Label htmlFor="lab-corpus-upload" className="sr-only">
            {t("labCorpusUploadLabel")}
          </Label>
          <input
            ref={fileRef}
            id="lab-corpus-upload"
            data-testid="lab-corpus-upload-input"
            type="file"
            multiple
            className="text-xs"
            disabled={disabled || busy || loading}
            onChange={(e) => void onUploadSelected(e.target.files)}
          />
        </div>
        {showAttachFromProject ? (
          <Button
            type="button"
            size="sm"
            variant="outline"
            data-testid="lab-corpus-attach-project"
            disabled={disabled || busy || loading}
            onClick={() => void attachAllFromOptionalProject()}
          >
            {t("labCorpusAttachFromProject")}
          </Button>
        ) : null}
      </div>

      {showAttachUnavailableHint ? (
        <p className="text-muted-foreground text-xs" data-testid="lab-corpus-attach-unavailable-hint">
          {t("labCorpusAttachNoSharedDocuments")}
        </p>
      ) : null}

      {showIndexProjectAttachHint ? (
        <p className="text-muted-foreground text-xs" data-testid="lab-corpus-index-project-hint">
          {t("labCorpusAttachIndexProjectHint")}
        </p>
      ) : null}

      {uploadProgress ? (
        <output className="text-muted-foreground block text-xs" data-testid="lab-corpus-upload-progress">
          {uploadProgress}
        </output>
      ) : null}

      {localWarn ? (
        <output
          role="status"
          className="block text-amber-700 dark:text-amber-400 text-xs"
          data-testid="lab-kb-duplicate-warning"
        >
          {localWarn}
        </output>
      ) : null}

      {displayErr ? (
        <output role="alert" className="block text-destructive text-xs" data-testid="lab-kb-error">
          {displayErr}
        </output>
      ) : null}
    </div>
  );
}
