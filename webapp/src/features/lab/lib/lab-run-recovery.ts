import type { AsyncTaskStatusDto, BenchmarkKind, LatestLabRunRecoveryDto, LabJobAcceptedDto } from "@/types/api";

export function taskStatusFromLatestRun(
  dto: LatestLabRunRecoveryDto,
  taskTypeHint: string,
): AsyncTaskStatusDto {
  const jobId = dto.jobId?.trim() || dto.evaluationRunId;
  return {
    id: jobId,
    taskType: taskTypeHint,
    status: dto.status?.trim() || "UNKNOWN",
    progressText: null,
    result: dto.result ?? null,
    errorMessage: null,
    terminal: dto.terminal,
    createdAt: "",
    updatedAt: "",
    startedAt: dto.startedAt ?? null,
    completedAt: dto.completedAt ?? null,
    failureCode: null,
  };
}

export function labJobAcceptedFromLatestRun(dto: LatestLabRunRecoveryDto): LabJobAcceptedDto | null {
  const jobId = dto.jobId?.trim();
  if (!jobId) return null;
  const pollPath = dto.pollPath?.trim() || `/lab/jobs/${jobId}`;
  return {
    jobId,
    status: dto.status?.trim() || "UNKNOWN",
    pollPath: pollPath as string,
    streamPath: (dto.streamPath?.trim() || `${pollPath}/events`) as string,
  };
}

/** True when backend latest-run query should run (no active recovery in flight). */
export function shouldFetchLatestLabRun(options: Readonly<{
  activeJobsLoading: boolean;
  recoveryDecisionKind: string;
  running: boolean;
  watchLive: boolean;
}>): boolean {
  if (options.activeJobsLoading || options.running || options.watchLive) {
    return false;
  }
  if (options.recoveryDecisionKind === "auto_follow" || options.recoveryDecisionKind === "cta") {
    return false;
  }
  if (options.recoveryDecisionKind === "session_only") {
    return false;
  }
  return true;
}

export function latestLabBenchmarkRunQueryKey(
  benchmarkKind: BenchmarkKind,
  projectId: string | null,
) {
  return ["lab", "benchmarks", benchmarkKind, "runs", "latest", projectId ?? "none"] as const;
}
