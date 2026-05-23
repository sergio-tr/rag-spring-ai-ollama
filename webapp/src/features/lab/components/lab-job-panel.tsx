"use client";

import { Button } from "@/components/ui/button";
import { InlineHelpStatus } from "@/features/help/InlineHelpStatus";
import {
  getLabJobStatusLabel,
  getLabJobUiPhase,
  labPhaseToTraceStatus,
} from "@/features/lab/lib/lab-task-ui";
import type { AsyncTaskStatusDto, LabJobAcceptedDto, LabJobLiveConnectionState } from "@/types/api";
import { useTranslations } from "next-intl";

type LabJobPanelProps = {
  accepted: LabJobAcceptedDto | null;
  taskStatus: AsyncTaskStatusDto | null;
  /** Shown while waiting for first SSE tick */
  queuedHint?: boolean;
  /** Legacy local abort flag — mapped to reconnecting copy, not a destructive error */
  stoppedWaiting?: boolean;
  /** Canonical live stream connection state from {@link useLabJobLiveEvents}. */
  connectionState?: LabJobLiveConnectionState | null;
  /** Monotonic seconds since async watch began (local UI clock). */
  watchElapsedSeconds?: number;
  onResumeLive?: () => void;
};

/**
 * Shared job progress UI: friendly status (Phase 3D) + collapsible technical details.
 */
export function LabJobPanel({
  accepted,
  taskStatus,
  queuedHint = false,
  stoppedWaiting = false,
  connectionState = null,
  watchElapsedSeconds,
  onResumeLive,
}: LabJobPanelProps) {
  const t = useTranslations("Lab");

  if (!accepted && !taskStatus && !stoppedWaiting && !connectionState) {
    return null;
  }

  const phase = getLabJobUiPhase({ taskStatus, queuedHint, stoppedWaiting, connectionState });
  const labels = {
    connecting: t("jobUiConnecting"),
    live: t("jobUiLive"),
    reconnecting: t("jobUiReconnecting"),
    resumed: t("jobUiResumed"),
    finishedAway: t("jobUiFinishedAway"),
    queued: t("jobUiQueued"),
    running: t("jobUiRunning"),
    completed: t("jobUiCompleted"),
    failed: t("jobUiFailed"),
    cancelled: t("jobUiCancelled"),
    stoppedWaiting: t("jobUiReconnecting"),
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

  const showResumeCta =
    onResumeLive != null &&
    (phase === "reconnecting" || phase === "stopped_waiting" || phase === "finished_away");

  return (
    <div className="bg-muted/30 space-y-3 rounded-md border p-3 text-sm" data-testid="lab-job-panel">
      {phase !== "idle" ? (
        <div className="flex flex-col gap-2">
          <InlineHelpStatus status={traceStatus} label={statusLabel} className="max-w-full" />
          {friendlyFailure ? (
            <output className="text-muted-foreground block text-xs">{friendlyFailure}</output>
          ) : null}
          {showResumeCta ? (
            <Button type="button" variant="outline" size="sm" className="h-7 w-fit text-xs" onClick={onResumeLive}>
              {t("jobRecoveryResumeHere")}
            </Button>
          ) : null}
          {watchElapsedSeconds != null &&
          watchElapsedSeconds >= 0 &&
          (phase === "connecting" ||
            phase === "live" ||
            phase === "queued" ||
            phase === "running" ||
            phase === "unknown_running" ||
            phase === "reconnecting" ||
            phase === "resumed") ? (
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
