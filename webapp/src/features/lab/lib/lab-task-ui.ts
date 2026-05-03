import type { AsyncTaskStatusDto } from "@/types/api";
import type { TraceStatus } from "@/features/trace/trace-types";

/** Normalized phases for UI + trace dedupe (backend uses AsyncTaskStatus strings). */
export type LabJobUiPhase =
  | "idle"
  | "queued"
  | "running"
  | "completed"
  | "failed"
  | "cancelled"
  | "stopped_waiting"
  | "unknown_running";

/**
 * Map async task DTO + UI hints to a single visible phase.
 * Does not surface raw API-only codes beyond a generic unknown-in-flight bucket.
 */
export function getLabJobUiPhase(input: {
  taskStatus: AsyncTaskStatusDto | null;
  queuedHint: boolean;
  stoppedWaiting: boolean;
}): LabJobUiPhase {
  const { taskStatus, queuedHint, stoppedWaiting } = input;
  if (stoppedWaiting) {
    return "stopped_waiting";
  }
  if (!taskStatus) {
    return queuedHint ? "queued" : "idle";
  }
  if (taskStatus.terminal) {
    const s = taskStatus.status.toUpperCase();
    if (s === "SUCCEEDED") return "completed";
    if (s === "FAILED") return "failed";
    if (s === "CANCELLED" || s === "CANCELED") return "cancelled";
    return "failed";
  }
  const s = taskStatus.status.toUpperCase();
  if (s === "QUEUED" || s === "PENDING") return "queued";
  if (s === "RUNNING") return "running";
  return "unknown_running";
}

/** Variant for {@link InlineHelpStatus} from Phase 3B. */
export function labPhaseToTraceStatus(phase: LabJobUiPhase): TraceStatus {
  switch (phase) {
    case "completed":
      return "success";
    case "failed":
    case "cancelled":
      return phase === "cancelled" ? "warning" : "error";
    case "stopped_waiting":
      return "warning";
    case "queued":
    case "running":
    case "unknown_running":
      return "in_progress";
    default:
      return "info";
  }
}

export type LabJobUiLabels = {
  queued: string;
  running: string;
  completed: string;
  failed: string;
  cancelled: string;
  stoppedWaiting: string;
  unknownRunning: string;
};

export function getLabJobStatusLabel(phase: LabJobUiPhase, labels: LabJobUiLabels): string {
  switch (phase) {
    case "queued":
      return labels.queued;
    case "running":
    case "unknown_running":
      return phase === "unknown_running" ? labels.unknownRunning : labels.running;
    case "completed":
      return labels.completed;
    case "failed":
      return labels.failed;
    case "cancelled":
      return labels.cancelled;
    case "stopped_waiting":
      return labels.stoppedWaiting;
    default:
      return labels.queued;
  }
}
