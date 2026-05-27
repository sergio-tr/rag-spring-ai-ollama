import type { AsyncTaskStatusDto, LabJobLiveConnectionState } from "@/types/api";
import type { TraceStatus } from "@/features/trace/trace-types";

/** Normalized phases for UI + trace dedupe (backend uses AsyncTaskStatus strings). */
export type LabJobUiPhase =
  | "idle"
  | "connecting"
  | "live"
  | "reconnecting"
  | "resumed"
  | "queued"
  | "running"
  | "cancelling"
  | "completed"
  | "failed"
  | "cancelled"
  | "finished_away"
  | "stopped_waiting"
  | "unknown_running";

function taskStatusUpper(status: string | null | undefined): string {
  return (status ?? "").trim().toUpperCase();
}

/**
 * Map async task DTO + live connection state to a single visible phase.
 */
export function getLabJobUiPhase(input: {
  taskStatus: AsyncTaskStatusDto | null;
  queuedHint?: boolean;
  stoppedWaiting?: boolean;
  connectionState?: LabJobLiveConnectionState | null;
}): LabJobUiPhase {
  const { taskStatus, queuedHint = false, stoppedWaiting = false, connectionState = null } = input;

  if (connectionState === "connecting" || connectionState === "accepted") return "connecting";
  if (connectionState === "connected") return "live";
  if (connectionState === "reconnecting" || connectionState === "resuming") return "reconnecting";
  if (connectionState === "stream_unavailable" || connectionState === "configuration_error") return "failed";
  if (connectionState === "resumed") return "resumed";
  if (connectionState === "live") {
    if (!taskStatus) return queuedHint ? "queued" : "running";
    if (taskStatus.terminal) {
      const s = taskStatusUpper(taskStatus.status);
      if (s === "SUCCEEDED") return "completed";
      if (s === "FAILED") return "failed";
      if (s === "CANCELLED" || s === "CANCELED") return "cancelled";
      return "failed";
    }
    const s = taskStatusUpper(taskStatus.status);
    if (s === "QUEUED" || s === "PENDING") return "queued";
    if (s === "CANCELLING") return "cancelling";
    if (s === "RUNNING") return "running";
    return "unknown_running";
  }
  if (connectionState === "finished_away") return "finished_away";
  if (connectionState === "completed") return "completed";
  if (connectionState === "failed") return "failed";
  if (connectionState === "cancelled") return "cancelled";

  if (stoppedWaiting) {
    return "reconnecting";
  }
  if (!taskStatus) {
    return queuedHint ? "queued" : "idle";
  }
  if (taskStatus.terminal) {
    const s = taskStatusUpper(taskStatus.status);
    if (s === "SUCCEEDED") return "completed";
    if (s === "FAILED") return "failed";
    if (s === "CANCELLED" || s === "CANCELED") return "cancelled";
    return "failed";
  }
  const s = taskStatusUpper(taskStatus.status);
  if (s === "QUEUED" || s === "PENDING") return "queued";
  if (s === "CANCELLING") return "cancelling";
  if (s === "RUNNING") return "running";
  return "unknown_running";
}

/** Variant for {@link InlineHelpStatus} from Phase 3B. */
export function labPhaseToTraceStatus(phase: LabJobUiPhase): TraceStatus {
  switch (phase) {
    case "completed":
      return "success";
    case "failed":
      return "error";
    case "cancelled":
    case "finished_away":
      return "warning";
    case "cancelling":
      return "warning";
    case "reconnecting":
    case "resumed":
    case "stopped_waiting":
      return "warning";
    case "connecting":
    case "live":
    case "queued":
    case "running":
    case "cancelling":
    case "unknown_running":
      return "in_progress";
    default:
      return "info";
  }
}

export type LabJobUiLabels = {
  connecting: string;
  live: string;
  reconnecting: string;
  resumed: string;
  finishedAway: string;
  queued: string;
  running: string;
  cancelling: string;
  completed: string;
  failed: string;
  cancelled: string;
  stoppedWaiting: string;
  unknownRunning: string;
  streamConfigurationError: string;
};

export function getLabJobStatusLabel(phase: LabJobUiPhase, labels: LabJobUiLabels, connectionState?: LabJobLiveConnectionState | null): string {
  if (connectionState === "configuration_error") {
    return labels.streamConfigurationError;
  }
  switch (phase) {
    case "connecting":
      return labels.connecting;
    case "live":
      return labels.live;
    case "reconnecting":
    case "stopped_waiting":
      return labels.reconnecting;
    case "resumed":
      return labels.resumed;
    case "finished_away":
      return labels.finishedAway;
    case "queued":
      return labels.queued;
    case "running":
    case "unknown_running":
      return phase === "unknown_running" ? labels.unknownRunning : labels.running;
    case "cancelling":
      return labels.cancelling;
    case "completed":
      return labels.completed;
    case "failed":
      return labels.failed;
    case "cancelled":
      return labels.cancelled;
    default:
      return labels.queued;
  }
}
