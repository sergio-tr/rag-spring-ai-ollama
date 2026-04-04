"use client";

import { Button } from "@/components/ui/button";
import type { AsyncTaskStatusDto, LabJobAcceptedDto } from "@/types/api";
import { useTranslations } from "next-intl";

type LabJobPanelProps = {
  accepted: LabJobAcceptedDto | null;
  taskStatus: AsyncTaskStatusDto | null;
  /** Shown while waiting for first poll/SSE tick */
  queuedHint?: boolean;
};

/**
 * Shared job progress UI: job id, API paths, and live status from async tasks.
 */
export function LabJobPanel({ accepted, taskStatus, queuedHint }: LabJobPanelProps) {
  const t = useTranslations("Lab");

  if (!accepted && !taskStatus) {
    return null;
  }

  const copyJobId = () => {
    if (accepted?.jobId && typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
      void navigator.clipboard.writeText(accepted.jobId);
    }
  };

  return (
    <div className="bg-muted/30 space-y-2 rounded-md border p-3 text-sm" data-testid="lab-job-panel">
      {accepted ? (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-muted-foreground">{t("jobIdLabel")}</span>
          <code className="rounded bg-muted px-1.5 py-0.5 text-xs">{accepted.jobId}</code>
          <Button type="button" variant="outline" size="sm" className="h-7 text-xs" onClick={copyJobId}>
            {t("jobCopyId")}
          </Button>
        </div>
      ) : null}
      {accepted ? (
        <div className="text-muted-foreground space-y-1 text-xs">
          <div>
            <span className="font-medium text-foreground">{t("jobPollPath")}</span>{" "}
            <code className="break-all">{accepted.pollPath}</code>
          </div>
          <div>
            <span className="font-medium text-foreground">{t("jobStreamPath")}</span>{" "}
            <code className="break-all">{accepted.streamPath}</code>
          </div>
        </div>
      ) : null}
      {queuedHint && !taskStatus ? (
        <p className="text-muted-foreground text-xs">{t("jobQueued")}</p>
      ) : null}
      {taskStatus ? (
        <div className="space-y-1 text-xs">
          <div className="flex flex-wrap gap-2">
            <span className="text-muted-foreground">{t("jobStatusField")}</span>
            <span className="font-medium">{taskStatus.status}</span>
            {taskStatus.terminal ? (
              <span className="text-muted-foreground">({t("jobTerminal")})</span>
            ) : null}
          </div>
          {taskStatus.progressText ? (
            <pre className="bg-background max-h-[200px] overflow-auto rounded border p-2 whitespace-pre-wrap">
              {taskStatus.progressText}
            </pre>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
