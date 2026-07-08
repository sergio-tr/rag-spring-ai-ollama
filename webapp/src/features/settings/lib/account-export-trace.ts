import { useTraceStore } from "@/features/trace/trace.store";
import type { AsyncTaskStatusDto } from "@/types/api";

/** Stable trace action id for tests and observability. */
export const ACCOUNT_EXPORT_TRACE_STOPPED_WATCHING = "account_export_stopped_watching";

export type AccountExportTraceMessages = {
  queued: string;
  running: string;
  completed: string;
  failed: string;
  cancelled: string;
};

export type AccountExportTraceDedupe = {
  runningEmitted: boolean;
  terminalEmitted: boolean;
};

export function createAccountExportTraceDedupe(): AccountExportTraceDedupe {
  return { runningEmitted: false, terminalEmitted: false };
}

export function traceAccountExportQueued(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "account",
    action: "account_export_queued",
    message,
    status: "info",
    metadata: { jobId },
  });
}

export function traceAccountExportRunning(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "account",
    action: "account_export_running",
    message,
    status: "in_progress",
    metadata: { jobId },
  });
}

export function traceAccountExportCompleted(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "account",
    action: "account_export_completed",
    message,
    status: "success",
    metadata: { jobId },
  });
}

export function traceAccountExportFailed(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "account",
    action: "account_export_failed",
    message,
    status: "error",
    metadata: { jobId },
  });
}

export function traceAccountExportCancelled(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "account",
    action: "account_export_cancelled",
    message,
    status: "warning",
    metadata: { jobId },
  });
}

/** Poll aborted or timed out locally - server export may still complete. */
export function traceAccountExportStoppedWaiting(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "account",
    action: ACCOUNT_EXPORT_TRACE_STOPPED_WATCHING,
    message,
    status: "warning",
    metadata: { jobId },
  });
}

export function traceAccountExportResumedWatching(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "account",
    action: "account_export_resumed_watching",
    message,
    status: "info",
    metadata: { jobId },
  });
}

/**
 * One trace row per logical phase while polling (queued is emitted separately).
 */
export function emitAccountExportTraceForTick(
  dedupe: AccountExportTraceDedupe,
  task: AsyncTaskStatusDto,
  jobId: string,
  messages: AccountExportTraceMessages,
): void {
  if (task.terminal) {
    if (dedupe.terminalEmitted) return;
    dedupe.terminalEmitted = true;
    const st = task.status.toUpperCase();
    if (st === "SUCCEEDED") {
      traceAccountExportCompleted(jobId, messages.completed);
      return;
    }
    if (st === "FAILED") {
      traceAccountExportFailed(jobId, messages.failed);
      return;
    }
    if (st === "CANCELLED" || st === "CANCELED") {
      traceAccountExportCancelled(jobId, messages.cancelled);
      return;
    }
    traceAccountExportFailed(jobId, messages.failed);
    return;
  }

  const st = task.status.toUpperCase();
  if (st === "RUNNING") {
    if (dedupe.runningEmitted) return;
    dedupe.runningEmitted = true;
    traceAccountExportRunning(jobId, messages.running);
  }
}
