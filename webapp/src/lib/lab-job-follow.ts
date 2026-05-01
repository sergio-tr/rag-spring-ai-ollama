import { pollLabJob } from "@/lib/async-task";
import { streamLabJob } from "@/lib/lab-job-sse";
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
  },
): Promise<AsyncTaskStatusDto> {
  const mode = options?.mode ?? "poll";
  if (mode === "sse") {
    return streamLabJob(accepted.streamPath, onTick, { signal: options?.signal });
  }
  return pollLabJob(accepted.jobId, onTick, {
    signal: options?.signal,
    intervalMs: options?.intervalMs,
    throwOnFailed: options?.throwOnFailed,
  });
}
