import {
  initialSnapshotFromAccepted,
  pickLatestRecordForSection,
  snapshotFromAsyncTask,
  upsertLabJobRecordList,
  type LabJobSectionKey,
  type PersistedLabJobRecord,
} from "@/features/lab/lib/lab-job-persistence";
import type { LabJobFollowMode } from "@/lib/lab-job-follow";
import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { AsyncTaskStatusDto, LabJobAcceptedDto } from "@/types/api";

type PendingResume = Readonly<{ sectionKey: LabJobSectionKey; jobId: string }>;

export type LabJobSessionStore = {
  /** Persisted via sessionStorage — survives SPA navigations and reloads within the tab. */
  records: PersistedLabJobRecord[];
  /** Ephemeral — not persisted. */
  pendingResume: PendingResume | null;
  resumeNonce: number;
  /** Bumped when user stops watching a job from the session banner. */
  forgetWatchNonce: number;

  upsertLabJobOnAccepted: (input: {
    accepted: LabJobAcceptedDto;
    sectionKey: LabJobSectionKey;
    followMode: LabJobFollowMode;
    taskTypeHint?: string;
    evaluationRunId?: string | null;
  }) => void;

  patchLabJobFromTick: (jobId: string, status: AsyncTaskStatusDto) => void;

  patchLabJobPollTimedOut: (jobId: string, lastStatus: AsyncTaskStatusDto | null) => void;

  setLabJobStoppedWatching: (jobId: string, stoppedWatching: boolean) => void;

  markLabJobStaleNotFound: (jobId: string) => void;

  dismissTerminalLabJob: (jobId: string) => void;

  clearLabJobRecord: (jobId: string) => void;

  /** Clears local watch tracking only; backend run/results are unchanged. */
  forgetLabJobWatching: (jobId: string) => void;

  /** Backend wins: remove other rows for the same section so a stale cache cannot override the active job. */
  clearOtherLabJobsForSection: (sectionKey: LabJobSectionKey, keepJobId: string) => void;

  requestResumeLabJob: (sectionKey: LabJobSectionKey, jobId: string) => void;

  consumePendingResume: (sectionKey: LabJobSectionKey) => PersistedLabJobRecord | null;

  pickLatestForSection: (sectionKey: LabJobSectionKey) => PersistedLabJobRecord | null;

  /** Clears volatile fields for tests (persisted records cleared separately). */
  __resetVolatileForTests: () => void;
};

export const useLabJobSessionStore = create<LabJobSessionStore>()(
  persist(
    (set, get) => ({
      records: [],
      pendingResume: null,
      resumeNonce: 0,
      forgetWatchNonce: 0,

      upsertLabJobOnAccepted: ({ accepted, sectionKey, followMode, taskTypeHint, evaluationRunId }) => {
        const now = Date.now();
        set((s) => {
          const existing = s.records.find((r) => r.jobId === accepted.jobId);
          const snap =
            existing?.lastStatus && existing.sectionKey === sectionKey
              ? existing.lastStatus
              : initialSnapshotFromAccepted(accepted, taskTypeHint ?? "LAB");
          const next: PersistedLabJobRecord = {
            jobId: accepted.jobId,
            sectionKey,
            accepted,
            evaluationRunId: evaluationRunId ?? existing?.evaluationRunId ?? null,
            followMode,
            startedAtMs: existing?.startedAtMs ?? now,
            lastUpdatedMs: now,
            lastStatus: snap,
            stoppedWatching: false,
            staleNotFound: false,
            pollTimedOut: false,
            dismissedTerminal: false,
          };
          return { records: upsertLabJobRecordList(s.records, next) };
        });
      },

      patchLabJobFromTick: (jobId, status) => {
        set((s) => {
          const existing = s.records.find((r) => r.jobId === jobId);
          if (!existing) return s;
          const terminal = status.terminal === true;
          const next: PersistedLabJobRecord = {
            ...existing,
            lastUpdatedMs: Date.now(),
            lastStatus: snapshotFromAsyncTask(status),
            staleNotFound: false,
            pollTimedOut: false,
            stoppedWatching: terminal ? false : existing.stoppedWatching,
          };
          return { records: upsertLabJobRecordList(s.records, next) };
        });
      },

      patchLabJobPollTimedOut: (jobId, lastStatus) => {
        set((s) => {
          const existing = s.records.find((r) => r.jobId === jobId);
          if (!existing) return s;
          const next: PersistedLabJobRecord = {
            ...existing,
            lastUpdatedMs: Date.now(),
            pollTimedOut: true,
            lastStatus: lastStatus ? snapshotFromAsyncTask(lastStatus) : existing.lastStatus,
          };
          return { records: upsertLabJobRecordList(s.records, next) };
        });
      },

      setLabJobStoppedWatching: (jobId, stoppedWatching) => {
        set((s) => {
          const existing = s.records.find((r) => r.jobId === jobId);
          if (!existing) return s;
          const next: PersistedLabJobRecord = {
            ...existing,
            stoppedWatching,
            lastUpdatedMs: Date.now(),
          };
          return { records: upsertLabJobRecordList(s.records, next) };
        });
      },

      markLabJobStaleNotFound: (jobId) => {
        set((s) => {
          const existing = s.records.find((r) => r.jobId === jobId);
          if (!existing) return s;
          const next: PersistedLabJobRecord = {
            ...existing,
            staleNotFound: true,
            pollTimedOut: false,
            lastUpdatedMs: Date.now(),
          };
          return { records: upsertLabJobRecordList(s.records, next) };
        });
      },

      dismissTerminalLabJob: (jobId) => {
        set((s) => {
          const existing = s.records.find((r) => r.jobId === jobId);
          if (!existing) return s;
          const next: PersistedLabJobRecord = {
            ...existing,
            dismissedTerminal: true,
            lastUpdatedMs: Date.now(),
          };
          return { records: upsertLabJobRecordList(s.records, next) };
        });
      },

      clearLabJobRecord: (jobId) => {
        set((s) => ({
          records: s.records.filter((r) => r.jobId !== jobId),
          pendingResume: s.pendingResume?.jobId === jobId ? null : s.pendingResume,
        }));
      },

      forgetLabJobWatching: (jobId) => {
        set((s) => ({
          records: s.records.filter((r) => r.jobId !== jobId),
          pendingResume: s.pendingResume?.jobId === jobId ? null : s.pendingResume,
          forgetWatchNonce: s.forgetWatchNonce + 1,
        }));
      },

      clearOtherLabJobsForSection: (sectionKey, keepJobId) => {
        set((s) => ({
          records: s.records.filter((r) => !(r.sectionKey === sectionKey && r.jobId !== keepJobId)),
          pendingResume:
            s.pendingResume?.sectionKey === sectionKey && s.pendingResume?.jobId !== keepJobId
              ? null
              : s.pendingResume,
        }));
      },

      requestResumeLabJob: (sectionKey, jobId) => {
        set((s) => ({
          pendingResume: { sectionKey, jobId },
          resumeNonce: s.resumeNonce + 1,
        }));
      },

      consumePendingResume: (sectionKey) => {
        const p = get().pendingResume;
        if (!p || p.sectionKey !== sectionKey) return null;
        const rec = get().records.find((r) => r.jobId === p.jobId) ?? null;
        set({ pendingResume: null });
        return rec;
      },

      pickLatestForSection: (sectionKey) => pickLatestRecordForSection(get().records, sectionKey),

      __resetVolatileForTests: () => set({ pendingResume: null, resumeNonce: 0, forgetWatchNonce: 0 }),
    }),
    {
      name: "rag-lab-jobs",
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({ records: state.records }),
    },
  ),
);
