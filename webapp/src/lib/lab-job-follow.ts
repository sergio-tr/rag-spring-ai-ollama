import { pollLabJob } from "@/lib/async-task";
import { streamLabJob, streamLabJobLive, type LabJobStreamCallbacks } from "@/lib/lab-job-sse";
import type { AsyncTaskStatusDto, LabJobAcceptedDto } from "@/types/api";

export type LabJobFollowMode = "poll" | "sse";

/**
 * After HTTP 202 + {@link LabJobAcceptedDto}, follow progress until terminal (polling or SSE).
 */
export async function followLabJob(
  accepted: LabJobAcceptedDto,
  onTick: (s: AsyncTaskStatusDto) => void,
  options?: {
    mode?: LabJobFollowMode;
    signal?: AbortSignal;
    intervalMs?: number;
    /** When false, terminal FAILED returns without throwing (e.g. chat assistant error persisted on message). */
    throwOnFailed?: boolean;
    /** Polling mode only — caps local watch duration; server job may continue. */
    maxWaitMs?: number;
    /** SSE resume cursor for reconnect (`?since=eventId`). */
    sinceEventId?: number | null;
    /** When true (default for SSE), uses auto reconnect/backoff until terminal. */
    liveReconnect?: boolean;
    callbacks?: LabJobStreamCallbacks;
  },
): Promise<AsyncTaskStatusDto> {
  const mode = options?.mode ?? "sse";
  if (mode === "sse" && (options?.liveReconnect ?? true)) {
    return streamLabJobLive(accepted.streamPath, {
      signal: options?.signal,
      sinceEventId: options?.sinceEventId,
      callbacks: {
        ...options?.callbacks,
        onTaskTick: onTick,
      },
    });
  }
  if (mode === "sse") {
    return streamLabJob(accepted.streamPath, onTick, {
      signal: options?.signal,
      sinceEventId: options?.sinceEventId,
    });
  }
  return pollLabJob(accepted.jobId, onTick, {
    signal: options?.signal,
    intervalMs: options?.intervalMs,
    throwOnFailed: options?.throwOnFailed,
    maxWaitMs: options?.maxWaitMs,
  });
}
