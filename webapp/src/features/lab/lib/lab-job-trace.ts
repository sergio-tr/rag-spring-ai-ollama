import { useTraceStore } from "@/features/trace/trace.store";
import type { AsyncTaskStatusDto } from "@/types/api";

export type LabJobTraceMessages = {
  queued: string;
  running: string;
  completed: string;
  failed: string;
  cancelled: string;
};

/** Dedupe flags for one Lab follow session (reset per Run click). */
export type LabJobTraceDedupe = {
  acceptedEmitted: boolean;
  runningEmitted: boolean;
  terminalEmitted: boolean;
};

export function createLabJobTraceDedupe(): LabJobTraceDedupe {
  return { acceptedEmitted: false, runningEmitted: false, terminalEmitted: false };
}

export function traceLabJobQueued(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "lab",
    action: "lab_job_queued",
    message,
    status: "info",
    metadata: { jobId },
  });
}

export function traceLabJobRunning(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "lab",
    action: "lab_job_running",
    message,
    status: "in_progress",
    metadata: { jobId },
  });
}

export function traceLabJobCompleted(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "lab",
    action: "lab_job_completed",
    message,
    status: "success",
    metadata: { jobId },
  });
}

export function traceLabJobFailed(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "lab",
    action: "lab_job_failed",
    message,
    status: "error",
    metadata: { jobId },
  });
}

export function traceLabJobCancelled(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "lab",
    action: "lab_job_cancelled",
    message,
    status: "warning",
    metadata: { jobId },
  });
}

export function traceLabJobStoppedWaiting(jobId: string, message: string): void {
  useTraceStore.getState().addTraceEvent({
    section: "lab",
    action: "lab_job_stopped_waiting",
    message,
    status: "warning",
    metadata: { jobId },
  });
}

/**
 * Emit trace events once per logical phase for polling/SSE ticks.
 * Job-accepted trace should be emitted separately via {@link traceLabJobQueued}.
 */
export function emitLabJobTraceForTick(
  dedupe: LabJobTraceDedupe,
  task: AsyncTaskStatusDto,
  jobId: string,
  messages: LabJobTraceMessages,
): void {
  if (task.terminal) {
    if (dedupe.terminalEmitted) return;
    dedupe.terminalEmitted = true;
    const st = task.status.toUpperCase();
    if (st === "SUCCEEDED") {
      traceLabJobCompleted(jobId, messages.completed);
      return;
    }
    if (st === "FAILED") {
      traceLabJobFailed(jobId, messages.failed);
      return;
    }
    if (st === "CANCELLED" || st === "CANCELED") {
      traceLabJobCancelled(jobId, messages.cancelled);
      return;
    }
    traceLabJobFailed(jobId, messages.failed);
    return;
  }

  const st = task.status.toUpperCase();
  if (st === "RUNNING" || st === "PROCESSING") {
    if (dedupe.runningEmitted) return;
    dedupe.runningEmitted = true;
    traceLabJobRunning(jobId, messages.running);
  }
}
