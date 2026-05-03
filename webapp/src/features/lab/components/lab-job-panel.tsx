"use client";

import { Button } from "@/components/ui/button";
import { InlineHelpStatus } from "@/features/help/InlineHelpStatus";
import {
  getLabJobStatusLabel,
  getLabJobUiPhase,
  labPhaseToTraceStatus,
} from "@/features/lab/lib/lab-task-ui";
import type { AsyncTaskStatusDto, LabJobAcceptedDto } from "@/types/api";
import { useTranslations } from "next-intl";

type LabJobPanelProps = {
  accepted: LabJobAcceptedDto | null;
  taskStatus: AsyncTaskStatusDto | null;
  /** Shown while waiting for first poll/SSE tick */
  queuedHint?: boolean;
  /** User aborted local wait; server job may still run */
  stoppedWaiting?: boolean;
  /** Monotonic seconds since async watch began (local UI clock). */
  watchElapsedSeconds?: number;
};

/**
 * Shared job progress UI: friendly status (Phase 3D) + collapsible technical details.
 */
export function LabJobPanel({
  accepted,
  taskStatus,
  queuedHint = false,
  stoppedWaiting = false,
  watchElapsedSeconds,
}: LabJobPanelProps) {
  const t = useTranslations("Lab");

  if (!accepted && !taskStatus && !stoppedWaiting) {
    return null;
  }

  const phase = getLabJobUiPhase({ taskStatus, queuedHint, stoppedWaiting });
  const labels = {
    queued: t("jobUiQueued"),
    running: t("jobUiRunning"),
    completed: t("jobUiCompleted"),
    failed: t("jobUiFailed"),
    cancelled: t("jobUiCancelled"),
    stoppedWaiting: t("jobUiStoppedWaiting"),
    unknownRunning: t("jobUiUnknownRunning"),
  };
  const statusLabel = getLabJobStatusLabel(phase, labels);
  const traceStatus = labPhaseToTraceStatus(phase);

  const copyJobId = () => {
    if (accepted?.jobId && typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
      void navigator.clipboard.writeText(accepted.jobId);
    }
  };

  const friendlyFailure =
    phase === "failed" && taskStatus?.errorMessage?.trim()
      ? taskStatus.errorMessage.trim().slice(0, 280)
      : null;

  return (
    <div className="bg-muted/30 space-y-3 rounded-md border p-3 text-sm" data-testid="lab-job-panel">
      {phase !== "idle" ? (
        <div className="flex flex-col gap-2">
          <InlineHelpStatus status={traceStatus} label={statusLabel} className="max-w-full" />
          {friendlyFailure ? (
            <p className="text-muted-foreground text-xs" role="status">
              {friendlyFailure}
            </p>
          ) : null}
          {watchElapsedSeconds != null &&
          watchElapsedSeconds >= 0 &&
          (phase === "queued" ||
            phase === "running" ||
            phase === "unknown_running" ||
            phase === "stopped_waiting") ? (
            <p className="text-muted-foreground text-xs" data-testid="lab-job-elapsed">
              {t("jobElapsedWatching", { sec: watchElapsedSeconds })}
            </p>
          ) : null}
        </div>
      ) : null}

      {accepted ? (
        <details className="text-xs">
          <summary className="cursor-pointer text-muted-foreground">{t("jobTechnicalDetails")}</summary>
          <div className="mt-2 space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-muted-foreground">{t("jobIdLabel")}</span>
              <code className="rounded bg-muted px-1.5 py-0.5 text-xs">{accepted.jobId}</code>
              <Button type="button" variant="outline" size="sm" className="h-7 text-xs" onClick={copyJobId}>
                {t("jobCopyId")}
              </Button>
            </div>
            <div className="text-muted-foreground space-y-1">
              <div>
                <span className="font-medium text-foreground">{t("jobPollPath")}</span>{" "}
                <code className="break-all">{accepted.pollPath}</code>
              </div>
              <div>
                <span className="font-medium text-foreground">{t("jobStreamPath")}</span>{" "}
                <code className="break-all">{accepted.streamPath}</code>
              </div>
            </div>
            {taskStatus ? (
              <div className="text-muted-foreground space-y-1 border-border border-t pt-2">
                <div className="flex flex-wrap gap-2">
                  <span>{t("jobStatusField")}</span>
                  <span className="font-medium text-foreground">{taskStatus.status}</span>
                  {taskStatus.terminal ? (
                    <span className="text-muted-foreground">({t("jobTerminal")})</span>
                  ) : null}
                </div>
              </div>
            ) : null}
          </div>
        </details>
      ) : stoppedWaiting ? (
        <p className="text-muted-foreground text-xs">{t("jobStoppedWaitingNoId")}</p>
      ) : null}

      {taskStatus?.progressText ? (
        <div className="space-y-1">
          <span className="text-muted-foreground text-xs font-medium">{t("jobProgress")}</span>
          <pre className="bg-background max-h-[200px] overflow-auto rounded border p-2 whitespace-pre-wrap text-xs">
            {taskStatus.progressText}
          </pre>
        </div>
      ) : null}
    </div>
  );
}
