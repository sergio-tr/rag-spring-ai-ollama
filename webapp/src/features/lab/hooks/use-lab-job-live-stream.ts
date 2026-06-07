"use client";

import type { LabJobAcceptedDto } from "@/types/api";
import { useMemo } from "react";
import {
  hydrateLabJobStatus,
  resumeLabJob,
  useLabJobSse,
  type UseLabJobSseOptions,
  type UseLabJobSseResult,
} from "./use-lab-job-sse";

export type UseLabJobLiveStreamOptions = Omit<UseLabJobSseOptions, "accepted"> &
  Readonly<{
    jobId: string | null;
    streamPath?: string | null;
    pollPath?: string | null;
    status?: string;
  }>;

function buildAccepted(input: Readonly<{
  jobId: string | null;
  streamPath?: string | null;
  pollPath?: string | null;
  status?: string;
}>): LabJobAcceptedDto | null {
  const jobId = input.jobId?.trim();
  if (!jobId) return null;
  return {
    jobId,
    status: input.status?.trim() || "UNKNOWN",
    pollPath: (input.pollPath?.trim() || `/lab/jobs/${jobId}`) as string,
    streamPath: (input.streamPath?.trim() || `/lab/jobs/${jobId}/events`) as string,
  };
}

/**
 * Canonical Lab live watcher: one-shot GET snapshot on attach, then SSE with reconnect.
 */
export function useLabJobLiveStream(options: UseLabJobLiveStreamOptions): UseLabJobSseResult {
  const { jobId, streamPath, pollPath, status, enabled = true, ...sseOptions } = options;
  const accepted = useMemo(
    () => buildAccepted({ jobId, streamPath, pollPath, status }),
    [jobId, streamPath, pollPath, status],
  );
  return useLabJobSse({
    ...sseOptions,
    accepted,
    enabled: enabled && accepted != null,
  });
}

export { hydrateLabJobStatus, resumeLabJob, useLabJobSse };
