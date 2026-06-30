"use client";

import { Button } from "@/components/ui/button";
import { InlineHelpStatus } from "@/features/help/InlineHelpStatus";
import {
  closureClassificationLabel,
  readBenchmarkClosureFromTask,
  isEmptyBenchmarkSuccess,
} from "@/features/lab/lib/lab-rag-closure";
import { UserFacingErrorNotice } from "@/lib/user-facing-error-notice";
import {
  getLabJobStatusLabel,
  getLabJobUiPhase,
  labTraceStatusForJob,
} from "@/features/lab/lib/lab-task-ui";
import {
  progressPercent,
  reduceLabJobEvents,
  type LabJobProgressPhase,
} from "@/features/lab/lib/lab-job-event-reducer";
import type { AsyncTaskStatusDto, LabJobAcceptedDto, LabJobEventDto, LabJobLiveConnectionState } from "@/types/api";
import { useTranslations } from "next-intl";
import { useMemo } from "react";

import {
  EMPTY_LAB_PROGRESS_SNAPSHOT,
  type LabProgressSnapshot,
} from "@/features/lab/lib/lab-job-progress-payload";

type LabJobPanelProps = Readonly<{
  accepted: LabJobAcceptedDto | null;
  taskStatus: AsyncTaskStatusDto | null;
  recentEvents?: LabJobEventDto[];
  progressSnapshot?: LabProgressSnapshot;
  /** Shown while waiting for first SSE tick */
  queuedHint?: boolean;
  /** Legacy local abort flag — mapped to reconnecting copy, not a destructive error */
  stoppedWaiting?: boolean;
  /** Canonical SSE connection state from {@link useLabJobLiveStream}. */
  connectionState?: LabJobLiveConnectionState | null;
  /** Monotonic seconds since async watch began (local UI clock). */
  watchElapsedSeconds?: number;
  /** Debug/fallback only — hidden in normal live-watcher flow. */
  showResumeFallback?: boolean;
  onResumeLive?: () => void;
}>;

function phaseLabelKey(phase: LabJobProgressPhase): string {
  switch (phase) {
    case "DATASET":
      return "jobProgressPhaseDataset";
    case "KNOWLEDGE_BASE":
      return "jobProgressPhaseKnowledgeBase";
    case "INDEXING":
      return "jobProgressPhaseIndexing";
    case "PLANNING":
      return "jobProgressPhasePlanning";
    case "RUNNING":
      return "jobProgressPhaseRunning";
    case "EXPORT":
      return "jobProgressPhaseExport";
    case "COMPLETED":
      return "jobProgressPhaseCompleted";
    case "FAILED":
      return "jobProgressPhaseFailed";
    case "ACCEPTED":
      return "jobProgressPhaseAccepted";
    default:
      return "jobProgressPhaseUnknown";
  }
}

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
  showResumeFallback = false,
  onResumeLive,
  recentEvents = [],
  progressSnapshot = EMPTY_LAB_PROGRESS_SNAPSHOT,
}: LabJobPanelProps) {
  const t = useTranslations("Lab");
  const progressView = useMemo(
    () => reduceLabJobEvents(recentEvents, progressSnapshot),
    [recentEvents, progressSnapshot],
  );
  const pct = progressPercent(progressView);

  if (!accepted && !taskStatus && !stoppedWaiting && connectionState == null) {
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
    cancelling: t("jobUiCancelling"),
    completed: t("jobUiCompleted"),
    completedWithFailures: t("jobUiCompletedWithFailures"),
    completedWithUnsupported: t("jobUiCompletedWithUnsupported"),
    noItemsExecuted: t("jobUiNoItemsExecuted"),
    failed: t("jobUiFailed"),
    cancelled: t("jobUiCancelled"),
    stoppedWaiting: t("jobUiReconnecting"),
    unknownRunning: t("jobUiUnknownRunning"),
    streamConfigurationError: t("jobUiStreamConfigurationError"),
  };
  const statusLabel = getLabJobStatusLabel(phase, labels, connectionState, taskStatus);
  const traceStatus = labTraceStatusForJob(phase, taskStatus);
  const benchmarkClosure = readBenchmarkClosureFromTask(taskStatus);
  const showEmptySuccessWarning = isEmptyBenchmarkSuccess(taskStatus);

  const copyJobId = () => {
    if (accepted?.jobId && typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
      void navigator.clipboard.writeText(accepted.jobId);
    }
  };

  const failureRaw =
    phase === "failed" && taskStatus?.errorMessage?.trim() ? taskStatus.errorMessage.trim() : null;
  const closureClassificationHint = benchmarkClosure
    ? closureClassificationLabel(benchmarkClosure.classification, t)
    : null;

  const showResumeCta =
    showResumeFallback &&
    onResumeLive != null &&
    (phase === "reconnecting" || phase === "stopped_waiting" || phase === "finished_away");

  const currentPhaseLabel = t(phaseLabelKey(progressView.phase));
  const itemCurrent =
    progressView.globalTotal != null && progressView.globalTotal > 0
      ? (progressView.currentItem ?? progressView.globalCompleted)
      : progressView.currentItem;
  const itemTotal =
    progressView.globalTotal != null && progressView.globalTotal > 0
      ? progressView.globalTotal
      : progressView.totalItems;
  const itemLine =
    itemCurrent != null && itemTotal != null && itemTotal > 0
      ? t("jobProgressItemCounter", { current: itemCurrent, total: itemTotal })
      : null;

  return (
    <div
      className="bg-muted/30 space-y-3 rounded-md border p-3 text-sm"
      data-testid="lab-job-panel"
      data-lab-job-ui-phase={phase === "idle" ? undefined : phase}
    >
      {phase === "idle" ? null : (
        <div className="flex flex-col gap-2">
          <InlineHelpStatus status={traceStatus} label={statusLabel} className="max-w-full" />
          {failureRaw ? (
            <UserFacingErrorNotice
              raw={failureRaw}
              fallback={t("jobUiFailed")}
              t={t}
              testId="lab-job-user-error"
              className="text-xs"
            />
          ) : null}
          {showEmptySuccessWarning ? (
            <output className="text-destructive block text-xs font-medium" data-testid="lab-empty-success-warning">
              {t("jobUiNoItemsExecutedDetail")}
            </output>
          ) : null}
          {benchmarkClosure && benchmarkClosure.expectedItems > 0 ? (
            <div className="space-y-1">
              {closureClassificationHint ? (
                <output
                  className="text-muted-foreground block text-xs font-medium"
                  data-testid="lab-benchmark-closure-classification"
                >
                  {closureClassificationHint}
                </output>
              ) : null}
              <output
                className="text-muted-foreground block text-xs"
                data-testid="lab-benchmark-closure-summary"
              >
                {t("jobBenchmarkClosureLine", {
                  expected: benchmarkClosure.expectedItems,
                  executed: benchmarkClosure.executedItems,
                  failed: benchmarkClosure.failedItems,
                  skipped: benchmarkClosure.skippedItems,
                  notSupported: benchmarkClosure.notSupportedItems,
                })}
              </output>
            </div>
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
      )}

      {recentEvents.length > 0 ? (
        <div className="space-y-2" data-testid="lab-progress-summary">
          <p className="text-foreground text-xs font-medium">
            {t("jobProgressCurrentPhase", { phase: currentPhaseLabel })}
          </p>
          {progressView.currentModelId ? (
            <p className="text-muted-foreground text-xs" data-testid="lab-job-active-model">
              {t("jobProgressActiveModel", { model: progressView.currentModelId })}
            </p>
          ) : null}
          {progressView.presetCode ? (
            <p className="text-muted-foreground text-xs" data-testid="lab-job-active-preset">
              {t("jobProgressActivePreset", { preset: progressView.presetCode })}
            </p>
          ) : null}
          {itemLine ? (
            <p className="text-muted-foreground text-xs" data-testid="lab-job-item-counter">
              {itemLine}
            </p>
          ) : null}
          {pct != null ? (
            <div className="space-y-1">
              <div
                className="bg-muted h-2 w-full overflow-hidden rounded-full"
                role="progressbar"
                aria-valuenow={pct}
                aria-valuemin={0}
                aria-valuemax={100}
                data-testid="lab-job-progress-bar"
              >
                <div className="bg-primary h-full transition-all duration-300" style={{ width: `${pct}%` }} />
              </div>
              <p className="text-muted-foreground text-xs">{t("jobProgressPercent", { pct })}</p>
            </div>
          ) : null}
          {progressView.lastAction ? (
            <p className="text-muted-foreground text-xs" data-testid="lab-job-last-action">
              {t("jobProgressLastAction", { action: progressView.lastAction })}
            </p>
          ) : null}
          {(progressView.itemsCompleted > 0 ||
            progressView.itemsFailed > 0 ||
            progressView.itemsSkipped > 0) && (
            <p className="text-muted-foreground text-xs" data-testid="lab-job-item-stats">
              {t("jobProgressItemStats", {
                completed: progressView.itemsCompleted,
                failed: progressView.itemsFailed,
                skipped: progressView.itemsSkipped,
              })}
            </p>
          )}
        </div>
      ) : null}

      {progressView.subtasks.length > 0 ? (
        <ul className="space-y-1" data-testid="lab-subtask-list">
          {progressView.subtasks.map((row) => (
            <li key={row.id} className="text-muted-foreground flex gap-2 text-xs">
              <span aria-hidden>{row.status === "done" ? "✓" : row.status === "failed" ? "✗" : "…"}</span>
              <span>{row.label}</span>
            </li>
          ))}
        </ul>
      ) : null}

      {accepted ? (
        <details className="text-xs" data-testid="lab-technical-events" open={false}>
          <summary className="cursor-pointer text-muted-foreground">{t("jobTechnicalDetails")}</summary>
          <div className="mt-2 space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-muted-foreground">{t("jobIdLabel")}</span>
              <code className="rounded bg-muted px-1.5 py-0.5 text-xs">{accepted.jobId}</code>
              <Button type="button" variant="outline" size="sm" className="h-7 text-xs" onClick={copyJobId}>
                {t("jobCopyId")}
              </Button>
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
            {progressView.technicalEvents.length > 0 ? (
              <ol className="text-muted-foreground max-h-28 space-y-0.5 overflow-y-auto border-t pt-2">
                {progressView.technicalEvents.map((event) => (
                  <li key={`tech-${event.eventId}-${event.type}`}>
                    {event.message?.trim() || event.type}
                  </li>
                ))}
              </ol>
            ) : null}
            {taskStatus?.progressText ? (
              <pre className="bg-background max-h-[160px] overflow-auto rounded border p-2 whitespace-pre-wrap text-xs">
                {taskStatus.progressText}
              </pre>
            ) : null}
          </div>
        </details>
      ) : null}
    </div>
  );
}
