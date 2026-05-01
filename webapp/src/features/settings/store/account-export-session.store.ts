import type { AccountJobAcceptedDto, AsyncTaskStatusDto } from "@/types/api";
import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

/** Slim snapshot for sessionStorage (survives SPA navigations within the tab). */
export type AccountExportStatusSnapshot = Readonly<
  Pick<
    AsyncTaskStatusDto,
    "id" | "taskType" | "status" | "terminal" | "progressText" | "errorMessage" | "failureCode"
  > & {
    result: Record<string, unknown> | null;
  }
>;

export function snapshotFromAsyncTask(status: AsyncTaskStatusDto): AccountExportStatusSnapshot {
  return {
    id: status.id,
    taskType: status.taskType,
    status: status.status,
    terminal: status.terminal,
    progressText: status.progressText,
    errorMessage: status.errorMessage,
    failureCode: status.failureCode ?? null,
    result: status.result,
  };
}

function initialSnapshotFromAccepted(accepted: AccountJobAcceptedDto): AccountExportStatusSnapshot {
  return {
    id: accepted.jobId,
    taskType: "ACCOUNT_EXPORT",
    status: accepted.status,
    terminal: false,
    progressText: null,
    errorMessage: null,
    failureCode: null,
    result: null,
  };
}

type AccountExportSessionState = {
  jobId: string | null;
  lastStatus: AccountExportStatusSnapshot | null;
  stoppedWatching: boolean;
  pollTimedOut: boolean;

  resetForNewExport: (accepted: AccountJobAcceptedDto) => void;
  patchFromTick: (status: AsyncTaskStatusDto) => void;
  markStoppedWatching: (pollTimedOut: boolean) => void;
  resumeWatching: () => void;
  clearSession: () => void;

  /** Clears volatile fields for tests. */
  __resetForTests: () => void;
};

const emptySlice = {
  jobId: null as string | null,
  lastStatus: null as AccountExportStatusSnapshot | null,
  stoppedWatching: false,
  pollTimedOut: false,
};

export const useAccountExportSessionStore = create<AccountExportSessionState>()(
  persist(
    (set) => ({
      ...emptySlice,

      resetForNewExport: (accepted) =>
        set({
          jobId: accepted.jobId,
          lastStatus: initialSnapshotFromAccepted(accepted),
          stoppedWatching: false,
          pollTimedOut: false,
        }),

      patchFromTick: (status) =>
        set({
          lastStatus: snapshotFromAsyncTask(status),
          ...(status.terminal ? { stoppedWatching: false, pollTimedOut: false } : {}),
        }),

      markStoppedWatching: (pollTimedOut) =>
        set({
          stoppedWatching: true,
          pollTimedOut,
        }),

      resumeWatching: () =>
        set({
          stoppedWatching: false,
          pollTimedOut: false,
        }),

      clearSession: () => set({ ...emptySlice }),

      __resetForTests: () => set({ ...emptySlice }),
    }),
    {
      name: "rag-account-export-session-v1",
      storage: createJSONStorage(() => sessionStorage),
      partialize: (s) => ({
        jobId: s.jobId,
        lastStatus: s.lastStatus,
        stoppedWatching: s.stoppedWatching,
        pollTimedOut: s.pollTimedOut,
      }),
    },
  ),
);
