"use client";

import type { LabJobSectionKey, PersistedLabJobRecord } from "@/features/lab/lib/lab-job-persistence";
import { pickLatestRecordForSection } from "@/features/lab/lib/lab-job-persistence";
import type { LabJobFollowMode } from "@/lib/lab-job-follow";
import type { ActiveLabJobDto, BenchmarkKind, LabJobAcceptedDto } from "@/types/api";
import { useMemo } from "react";

/** Backend-driven resume candidate (single source: GET /lab/jobs/active). */
export type LabActiveJobResumeCandidate = Readonly<{
  jobId: string;
  evaluationRunId: string;
  sectionKey: LabJobSectionKey;
  benchmarkKind: BenchmarkKind;
  projectId: string | null;
  accepted: LabJobAcceptedDto;
  resolvedFollowMode: LabJobFollowMode;
  orderingTimestampMs: number;
  source: "backend-active-job";
}>;

export type LabActiveJobResumptionDecision =
  | { kind: "none" }
  | { kind: "auto_follow"; candidate: LabActiveJobResumeCandidate }
  | { kind: "cta"; candidates: LabActiveJobResumeCandidate[]; reason: string }
  | { kind: "session_only"; record: PersistedLabJobRecord; reason: string };

export type LabActiveJobResumptionInputs = Readonly<{
  sectionKey: LabJobSectionKey;
  benchmarkKind: BenchmarkKind;
  activeProjectId: string | null;
  draftFollowMode: LabJobFollowMode | null;
  backendActiveJobs: ActiveLabJobDto[] | null;
  /** True until the active-jobs query has completed at least once (success or error). */
  backendActiveJobsLoading: boolean;
  backendActiveJobsError: unknown | null;
  sessionRecords: readonly PersistedLabJobRecord[];
}>;

export function expectedBenchmarkKindForSection(sectionKey: LabJobSectionKey): BenchmarkKind | null {
  switch (sectionKey) {
    case "evaluation-llm":
      return "LLM_JUDGE_QA";
    case "evaluation-embedding":
      return "EMBEDDING_RETRIEVAL";
    case "evaluation-rag":
      return "RAG_PRESET_END_TO_END";
    default:
      return null;
  }
}

/** M0 - card must use a section key consistent with the benchmark kind. */
export function isSectionBenchmarkConsistent(sectionKey: LabJobSectionKey, benchmarkKind: BenchmarkKind): boolean {
  const expected = expectedBenchmarkKindForSection(sectionKey);
  return expected != null && expected === benchmarkKind;
}

function norm(s: string | null | undefined): string | null {
  const t = s?.trim();
  return t ? t.toLowerCase() : null;
}

/** M1 + M2 - benchmark kind (case-insensitive) and project scope. */
export function activeJobMatchesCard(
  job: ActiveLabJobDto,
  benchmarkKind: BenchmarkKind,
  activeProjectId: string | null,
): boolean {
  const jk = job.benchmarkKind?.trim();
  if (!jk || !job.jobId?.trim()) return false;
  if (jk.toUpperCase() !== benchmarkKind.toUpperCase()) return false;

  const jp = norm(job.projectId);
  const cp = norm(activeProjectId);
  // Lab-only jobs (no project) always match - LAB must not require an active project.
  if (!jp) return true;
  // Project-scoped job: match when card has no active project or the same project.
  if (!cp) return true;
  return cp === jp;
}

function parseInstantMs(iso: string | null | undefined): number | null {
  if (!iso?.trim()) return null;
  const ms = Date.parse(iso);
  return Number.isFinite(ms) ? ms : null;
}

/** Ordering fingerprint for ambiguity checks (startedAt + updatedAt only; excludes jobId). */
export function orderingKeyForActiveJob(job: ActiveLabJobDto): string {
  const s = parseInstantMs(job.startedAt);
  const u = parseInstantMs(job.updatedAt);
  return `${s ?? "na"}|${u ?? "na"}`;
}

function activeJobToAccepted(job: ActiveLabJobDto): LabJobAcceptedDto {
  const jid = job.jobId.trim();
  return {
    jobId: jid,
    status: job.status?.trim() || "UNKNOWN",
    pollPath: (job.pollPath?.trim() || `/lab/jobs/${jid}`) as string,
    streamPath: (job.streamPath?.trim() || `/lab/jobs/${jid}/events`) as string,
  };
}

function toCandidate(
  job: ActiveLabJobDto,
  sectionKey: LabJobSectionKey,
  benchmarkKind: BenchmarkKind,
  resolvedFollowMode: LabJobFollowMode,
): LabActiveJobResumeCandidate {
  const started = parseInstantMs(job.startedAt);
  const updated = parseInstantMs(job.updatedAt);
  const orderingTimestampMs = started ?? updated ?? 0;
  return {
    jobId: job.jobId.trim(),
    evaluationRunId: job.evaluationRunId.trim(),
    sectionKey,
    benchmarkKind,
    projectId: job.projectId?.trim() ? job.projectId.trim() : null,
    accepted: activeJobToAccepted(job),
    resolvedFollowMode,
    orderingTimestampMs,
    source: "backend-active-job",
  };
}

export function sessionRecordToCandidate(
  record: PersistedLabJobRecord,
  sectionKey: LabJobSectionKey,
  benchmarkKind: BenchmarkKind,
  resolvedFollowMode: LabJobFollowMode,
): LabActiveJobResumeCandidate {
  return {
    jobId: record.jobId,
    evaluationRunId: record.evaluationRunId?.trim() ?? "",
    sectionKey,
    benchmarkKind,
    projectId: null,
    accepted: record.accepted,
    resolvedFollowMode,
    orderingTimestampMs: record.lastUpdatedMs,
    source: "backend-active-job",
  };
}

function pickSessionOnlyRecord(
  sectionKey: LabJobSectionKey,
  sessionRecords: readonly PersistedLabJobRecord[],
): PersistedLabJobRecord | null {
  const latest = pickLatestRecordForSection(sessionRecords, sectionKey);
  if (!latest || latest.staleNotFound || latest.dismissedTerminal) {
    return null;
  }
  if (latest.lastStatus?.terminal === true) {
    return null;
  }
  return latest;
}

/**
 * Deterministic resumption decision: backend active jobs win over session cache when both exist.
 * Does not perform network I/O.
 */
export function computeLabActiveJobResumption(params: LabActiveJobResumptionInputs): LabActiveJobResumptionDecision {
  if (!isSectionBenchmarkConsistent(params.sectionKey, params.benchmarkKind)) {
    return { kind: "none" };
  }

  const resolvedFollowMode: LabJobFollowMode = "sse";

  if (params.backendActiveJobsLoading) {
    return { kind: "none" };
  }

  if (params.backendActiveJobsError) {
    const sessionRecord = pickSessionOnlyRecord(params.sectionKey, params.sessionRecords);
    if (sessionRecord) {
      return {
        kind: "session_only",
        record: sessionRecord,
        reason: "backend_active_jobs_error",
      };
    }
    return { kind: "none" };
  }

  const jobs = params.backendActiveJobs ?? [];
  const matched = jobs.filter((j) => activeJobMatchesCard(j, params.benchmarkKind, params.activeProjectId));

  if (matched.length === 0) {
    const sessionRecord = pickSessionOnlyRecord(params.sectionKey, params.sessionRecords);
    if (sessionRecord) {
      return {
        kind: "session_only",
        record: sessionRecord,
        reason: "session_inflight_no_backend_active_job",
      };
    }
    return { kind: "none" };
  }

  if (matched.length === 1) {
    return {
      kind: "auto_follow",
      candidate: toCandidate(matched[0], params.sectionKey, params.benchmarkKind, resolvedFollowMode),
    };
  }

  const sorted = [...matched].sort((a, b) => {
    const sa = parseInstantMs(a.startedAt) ?? parseInstantMs(a.updatedAt) ?? 0;
    const sb = parseInstantMs(b.startedAt) ?? parseInstantMs(b.updatedAt) ?? 0;
    if (sb !== sa) return sb - sa;
    const ua = parseInstantMs(a.updatedAt) ?? 0;
    const ub = parseInstantMs(b.updatedAt) ?? 0;
    if (ub !== ua) return ub - ua;
    return a.jobId.localeCompare(b.jobId);
  });

  const top = sorted[0];
  const second = sorted[1];
  if (orderingKeyForActiveJob(top) === orderingKeyForActiveJob(second)) {
    return {
      kind: "cta",
      candidates: sorted.map((j) => toCandidate(j, params.sectionKey, params.benchmarkKind, resolvedFollowMode)),
      reason: "ambiguous_multiple_active_jobs",
    };
  }

  return {
    kind: "auto_follow",
    candidate: toCandidate(top, params.sectionKey, params.benchmarkKind, resolvedFollowMode),
  };
}

export type LabActiveJobResumptionResult = Readonly<{
  decision: LabActiveJobResumptionDecision;
  resolvedFollowMode: LabJobFollowMode;
}>;

export function useLabActiveJobResumption(params: LabActiveJobResumptionInputs): LabActiveJobResumptionResult {
  const {
    sectionKey,
    benchmarkKind,
    activeProjectId,
    draftFollowMode,
    backendActiveJobs,
    backendActiveJobsLoading,
    backendActiveJobsError,
    sessionRecords,
  } = params;
  return useMemo(() => {
    const decision = computeLabActiveJobResumption({
      sectionKey,
      benchmarkKind,
      activeProjectId,
      draftFollowMode,
      backendActiveJobs,
      backendActiveJobsLoading,
      backendActiveJobsError,
      sessionRecords,
    });
    const resolvedFollowMode: LabJobFollowMode = "sse";
    return { decision, resolvedFollowMode };
  }, [
    sectionKey,
    benchmarkKind,
    activeProjectId,
    draftFollowMode,
    backendActiveJobs,
    backendActiveJobsLoading,
    backendActiveJobsError,
    sessionRecords,
  ]);
}
