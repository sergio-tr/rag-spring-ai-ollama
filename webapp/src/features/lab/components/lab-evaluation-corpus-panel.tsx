"use client";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { useEvaluationCorpus } from "@/features/lab/hooks/use-evaluation-corpus";
import {
  corpusUploadErrorMessage,
  summarizeCorpusUploadFailures,
} from "@/features/lab/lib/evaluation-corpus-upload";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { ProjectDocumentDto } from "@/types/api";
import { useTranslations } from "next-intl";
import { useCallback, useRef, useState } from "react";

export type LabEvaluationCorpusPanelProps = {
  corpusId: string | null;
  onCorpusIdChange: (corpusId: string | null) => void;
  /** Optional project to reuse documents from (not required). */
  optionalProjectId?: string | null;
  disabled?: boolean;
};

function formatDocumentStatus(status: string, t: (key: string) => string): string {
  if (status === "INGESTING" || status === "PROCESSING") {
    return t("labCorpusStatusProcessing");
  }
  if (status === "ERROR" || status === "FAILED") {
    return t("labCorpusStatusFailed");
  }
  if (status === "READY") {
    return t("labCorpusStatusReady");
  }
  return status;
}

export function LabEvaluationCorpusPanel({
  corpusId,
  onCorpusIdChange,
  optionalProjectId,
  disabled = false,
}: LabEvaluationCorpusPanelProps) {
  const t = useTranslations("Lab");
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<string | null>(null);
  const [localErr, setLocalErr] = useState<string | null>(null);
  const { summary, loading, error, ensureCorpus, uploadDocuments, attachFromProject } =
    useEvaluationCorpus(corpusId);

  const mapUploadError = useCallback(
    (message: string) => {
      if (message === "FILE_TOO_LARGE") {
        return t("labCorpusFileTooLarge");
      }
      if (message === "UNSUPPORTED_TYPE") {
        return t("labCorpusUnsupportedType");
      }
      return message;
    },
    [t],
  );

  const ensureReady = useCallback(async () => {
    if (corpusId) return corpusId;
    const created = await ensureCorpus();
    onCorpusIdChange(created.id);
    return created.id;
  }, [corpusId, ensureCorpus, onCorpusIdChange]);

  async function onUploadSelected(files: FileList | null | undefined) {
    const list = files ? Array.from(files).filter((f) => f.size > 0) : [];
    if (list.length === 0 || disabled) return;
    setBusy(true);
    setLocalErr(null);
    setUploadProgress(t("labCorpusUploadProgress", { current: 0, total: list.length }));
    try {
      const id = await ensureReady();
      const { response } = await uploadDocuments(id, list, (current, total) => {
        setUploadProgress(t("labCorpusUploadProgress", { current, total }));
      });
      const partial = summarizeCorpusUploadFailures(response);
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

  async function attachAllFromOptionalProject() {
    if (!optionalProjectId || disabled) return;
    setBusy(true);
    setLocalErr(null);
    try {
      const id = await ensureReady();
      const docs = await apiFetch<ProjectDocumentDto[]>(
        apiProductPath(`/projects/${optionalProjectId}/documents`),
      );
      const sharedIds = docs.filter((d) => d.corpusScope === "PROJECT_SHARED").map((d) => d.id);
      if (sharedIds.length === 0) {
        setLocalErr(t("labCorpusNoProjectDocuments"));
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

  const displayErr = localErr ?? error;
  const docCount = summary?.documentCount ?? 0;
  const readyCount = summary?.readyCount ?? 0;

  return (
    <div
      className="space-y-3 rounded-md border bg-muted/20 p-3 text-sm"
      data-testid="lab-evaluation-corpus-panel"
      data-lab-knowledge-base-panel=""
    >
      <div>
        <p className="font-medium text-foreground">{t("labCorpusTitle")}</p>
        <p className="text-muted-foreground mt-1 text-xs leading-relaxed">{t("labCorpusHelp")}</p>
      </div>

      <p className="text-muted-foreground text-xs" data-testid="lab-corpus-summary">
        {t("labCorpusSelectedSummary", { total: docCount, ready: readyCount })}
      </p>

      {summary?.documents && summary.documents.length > 0 ? (
        <ul className="text-muted-foreground max-h-28 list-inside list-disc overflow-y-auto text-xs">
          {summary.documents.map((d) => (
            <li key={d.id}>
              {d.fileName} — {formatDocumentStatus(d.status, t)}
              {d.errorMessage ? ` (${d.errorMessage})` : null}
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
        {optionalProjectId ? (
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

      {uploadProgress ? (
        <output className="text-muted-foreground block text-xs" data-testid="lab-corpus-upload-progress">
          {uploadProgress}
        </output>
      ) : null}

      {displayErr ? (
        <output role="alert" className="block text-destructive text-xs">
          {displayErr}
        </output>
      ) : null}
    </div>
  );
}
