"use client";

import type { LabJobSectionKey } from "@/features/lab/lib/lab-job-persistence";
import { useLabJobSessionStore } from "@/features/lab/store/lab-job-session.store";
import { ApiError } from "@/lib/api-client";
import { fetchLabJobStatusOnce } from "@/lib/async-task";
import type { AsyncTaskStatusDto, BenchmarkKind } from "@/types/api";
import { useCallback, useEffect, useRef } from "react";
import { useActiveLabJobs } from "./use-active-lab-jobs";
import {
  type LabActiveJobResumptionDecision,
  type LabActiveJobResumeCandidate,
  sessionRecordToCandidate,
  useLabActiveJobResumption,
} from "./use-lab-active-job-resumption";

export type AutoResumeLabJobsFollowInput = Readonly<{
  candidate: LabActiveJobResumeCandidate;
  status: AsyncTaskStatusDto;
}>;

export type UseAutoResumeLabJobsOptions = Readonly<{
  sectionKey: LabJobSectionKey;
  benchmarkKind: BenchmarkKind;
  activeProjectId: string | null;
  taskTypeHint: string;
  onAutoFollow: (input: AutoResumeLabJobsFollowInput) => void | Promise<void>;
  /** When false, skip automatic SSE attach (e.g. user started another run). */
  canAutoFollow?: boolean;
  /** Job id already being watched in this card — suppress duplicate auto-follow. */
  watchingJobId?: string | null;
  onFollowError?: (error: unknown, candidate: LabActiveJobResumeCandidate) => void;
}>;

export type UseAutoResumeLabJobsResult = Readonly<{
  decision: LabActiveJobResumptionDecision;
  activeJobsLoading: boolean;
  activeJobsError: unknown | null;
  followCandidate: (candidate: LabActiveJobResumeCandidate) => Promise<void>;
}>;

/**
 * Backend-driven auto-resume: GET /lab/jobs/active once, auto-follow when unambiguous.
 */
export function useAutoResumeLabJobs(options: UseAutoResumeLabJobsOptions): UseAutoResumeLabJobsResult {
  const {
    sectionKey,
    benchmarkKind,
    activeProjectId,
    taskTypeHint,
    onAutoFollow,
    canAutoFollow = true,
    watchingJobId,
    onFollowError,
  } = options;

  const activeJobs = useActiveLabJobs();
  const sessionRecords = useLabJobSessionStore((s) => s.records);
  const clearOtherLabJobsForSection = useLabJobSessionStore((s) => s.clearOtherLabJobsForSection);
  const autoFollowHandledRef = useRef<string | null>(null);

  const recovery = useLabActiveJobResumption({
    sectionKey,
    benchmarkKind,
    activeProjectId,
    draftFollowMode: "sse",
    backendActiveJobs: activeJobs.data ?? null,
    backendActiveJobsLoading: !activeJobs.isFetched,
    backendActiveJobsError: activeJobs.isError ? activeJobs.error : null,
    sessionRecords,
  });

  const followCandidate = useCallback(
    async (candidate: LabActiveJobResumeCandidate) => {
      clearOtherLabJobsForSection(sectionKey, candidate.jobId);
      useLabJobSessionStore.getState().upsertLabJobOnAccepted({
        accepted: candidate.accepted,
        sectionKey,
        followMode: "sse",
        taskTypeHint,
        evaluationRunId: candidate.evaluationRunId,
      });
      const status = await fetchLabJobStatusOnce(candidate.jobId);
      useLabJobSessionStore.getState().patchLabJobFromTick(candidate.jobId, status);
      await onAutoFollow({ candidate, status });
    },
    [clearOtherLabJobsForSection, onAutoFollow, sectionKey, taskTypeHint],
  );

  useEffect(() => {
    if (!canAutoFollow) {
      return;
    }

    if (recovery.decision.kind === "auto_follow") {
      const candidate = recovery.decision.candidate;
      if (watchingJobId && watchingJobId !== candidate.jobId) {
        return;
      }
      if (autoFollowHandledRef.current === candidate.jobId) {
        return;
      }

      autoFollowHandledRef.current = candidate.jobId;
      void (async () => {
        try {
          await followCandidate(candidate);
        } catch (e) {
          autoFollowHandledRef.current = null;
          if (e instanceof ApiError && e.status === 404) {
            useLabJobSessionStore.getState().markLabJobStaleNotFound(candidate.jobId);
          }
          onFollowError?.(e, candidate);
        }
      })();
      return;
    }

    if (recovery.decision.kind === "session_only") {
      const record = recovery.decision.record;
      if (watchingJobId && watchingJobId !== record.jobId) {
        return;
      }
      if (autoFollowHandledRef.current === record.jobId) {
        return;
      }

      autoFollowHandledRef.current = record.jobId;
      const candidate = sessionRecordToCandidate(record, sectionKey, benchmarkKind, "sse");
      void (async () => {
        try {
          const status = await fetchLabJobStatusOnce(record.jobId);
          useLabJobSessionStore.getState().patchLabJobFromTick(record.jobId, status);
          await onAutoFollow({ candidate, status });
        } catch (e) {
          autoFollowHandledRef.current = null;
          if (e instanceof ApiError && e.status === 404) {
            useLabJobSessionStore.getState().markLabJobStaleNotFound(record.jobId);
          }
          onFollowError?.(e, candidate);
        }
      })();
    }
  }, [
    canAutoFollow,
    benchmarkKind,
    followCandidate,
    onAutoFollow,
    onFollowError,
    recovery.decision,
    sectionKey,
    watchingJobId,
  ]);

  return {
    decision: recovery.decision,
    activeJobsLoading: !activeJobs.isFetched,
    activeJobsError: activeJobs.isError ? activeJobs.error : null,
    followCandidate,
  };
}
