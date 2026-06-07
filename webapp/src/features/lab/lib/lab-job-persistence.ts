import type { LabJobFollowMode } from "@/lib/lab-job-follow";
import type { AsyncTaskStatusDto, LabJobAcceptedDto } from "@/types/api";

/** Cap persisted Lab jobs to avoid unbounded session growth. */
export const MAX_LAB_JOB_RECORDS = 5;

export type LabJobSectionKey =
  | "classifier-train"
  | "classifier-eval"
  | "evaluation-llm"
  | "evaluation-rag"
  | "evaluation-embedding";

/** Product route path without locale (next-intl adds locale). */
export function labSectionHref(sectionKey: LabJobSectionKey): string {
  switch (sectionKey) {
    case "classifier-train":
    case "classifier-eval":
      return "/lab/classifier";
    case "evaluation-llm":
      return "/lab/evaluation/llm";
    case "evaluation-rag":
      return "/lab/evaluation/rag";
    case "evaluation-embedding":
      return "/lab/evaluation/embedding";
  }
}

export function pathnameMatchesLabSection(pathname: string | null, sectionKey: LabJobSectionKey): boolean {
  if (!pathname) return false;
  if (sectionKey === "classifier-train" || sectionKey === "classifier-eval") {
    return pathname.includes("/lab/classifier");
  }
  if (sectionKey === "evaluation-llm") {
    return pathname.includes("/lab/evaluation/llm");
  }
  if (sectionKey === "evaluation-embedding") {
    return pathname.includes("/lab/evaluation/embedding");
  }
  return pathname.includes("/lab/evaluation/rag");
}

/** Slim snapshot persisted between navigations (full {@link AsyncTaskStatusDto} returns on next poll). */
export type PersistedLabJobStatusSnapshot = Readonly<
  Pick<
    AsyncTaskStatusDto,
    "id" | "taskType" | "status" | "terminal" | "progressText" | "errorMessage" | "failureCode"
  > & {
    result: Record<string, unknown> | null;
  }
>;

export type PersistedLabJobRecord = Readonly<{
  jobId: string;
  sectionKey: LabJobSectionKey;
  accepted: LabJobAcceptedDto;
  /** Present for typed benchmark runs ({@code /lab/benchmarks/…/runs}). */
  evaluationRunId?: string | null;
  followMode: LabJobFollowMode;
  startedAtMs: number;
  lastUpdatedMs: number;
  lastStatus: PersistedLabJobStatusSnapshot | null;
  stoppedWatching: boolean;
  staleNotFound: boolean;
  pollTimedOut: boolean;
  dismissedTerminal: boolean;
}>;

export function snapshotFromAsyncTask(status: AsyncTaskStatusDto): PersistedLabJobStatusSnapshot {
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

export function initialSnapshotFromAccepted(
  accepted: LabJobAcceptedDto,
  taskTypeHint: string,
): PersistedLabJobStatusSnapshot {
  return {
    id: accepted.jobId,
    taskType: taskTypeHint,
    status: accepted.status,
    terminal: false,
    progressText: null,
    errorMessage: null,
    failureCode: null,
    result: null,
  };
}

/** Rebuild API DTO for {@link LabJobPanel} after hydration (timestamps may be empty). */
export function asyncTaskDtoFromSnapshot(
  jobId: string,
  snap: PersistedLabJobStatusSnapshot | null,
): AsyncTaskStatusDto | null {
  if (!snap) return null;
  return {
    id: snap.id || jobId,
    taskType: snap.taskType,
    status: snap.status,
    progressText: snap.progressText ?? null,
    result: snap.result ?? null,
    errorMessage: snap.errorMessage ?? null,
    terminal: snap.terminal,
    createdAt: "",
    updatedAt: "",
    startedAt: null,
    completedAt: null,
    failureCode: snap.failureCode ?? null,
  };
}

export function trimLabJobRecords(records: readonly PersistedLabJobRecord[]): PersistedLabJobRecord[] {
  if (records.length <= MAX_LAB_JOB_RECORDS) return [...records];
  return [...records]
    .sort((a, b) => b.lastUpdatedMs - a.lastUpdatedMs)
    .slice(0, MAX_LAB_JOB_RECORDS);
}

export function upsertLabJobRecordList(
  records: readonly PersistedLabJobRecord[],
  next: PersistedLabJobRecord,
): PersistedLabJobRecord[] {
  const without = records.filter((r) => r.jobId !== next.jobId);
  return trimLabJobRecords([...without, next]);
}

export function pickLatestRecordForSection(
  records: readonly PersistedLabJobRecord[],
  sectionKey: LabJobSectionKey,
): PersistedLabJobRecord | null {
  const filtered = records.filter((r) => r.sectionKey === sectionKey);
  if (filtered.length === 0) return null;
  const [head, ...tail] = filtered;
  return tail.reduce((a, b) => (a.lastUpdatedMs >= b.lastUpdatedMs ? a : b), head);
}

function recordIsTerminal(rec: PersistedLabJobRecord): boolean {
  return rec.lastStatus?.terminal === true;
}

/**
 * Pick one row for the Lab session banner (priority: stale → stopped waiting → in-flight → undismissed terminal).
 */
export function pickPrimaryLabBannerRecord(records: readonly PersistedLabJobRecord[]): PersistedLabJobRecord | null {
  const visible = records.filter((r) => {
    if (r.dismissedTerminal && recordIsTerminal(r)) return false;
    return true;
  });
  if (visible.length === 0) return null;

  const newest = (xs: PersistedLabJobRecord[]): PersistedLabJobRecord | null => {
    if (xs.length === 0) return null;
    const [head, ...tail] = xs;
    return tail.reduce((a, b) => (a.lastUpdatedMs >= b.lastUpdatedMs ? a : b), head);
  };

  const stale = visible.filter((r) => r.staleNotFound);
  const pickedStale = newest(stale);
  if (pickedStale) return pickedStale;

  const stopped = visible.filter((r) => r.stoppedWatching && !recordIsTerminal(r));
  const pickedStopped = newest(stopped);
  if (pickedStopped) return pickedStopped;

  const inflight = visible.filter((r) => !recordIsTerminal(r) && !r.staleNotFound);
  const pickedInflight = newest(inflight);
  if (pickedInflight) return pickedInflight;

  const terminal = visible.filter((r) => recordIsTerminal(r));
  return newest(terminal);
}
