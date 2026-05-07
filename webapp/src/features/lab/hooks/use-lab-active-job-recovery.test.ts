import { describe, it, expect } from "vitest";
import {
  activeJobMatchesCard,
  computeLabActiveJobRecovery,
  isSectionBenchmarkConsistent,
  orderingKeyForActiveJob,
} from "./use-lab-active-job-recovery";
import type { ActiveLabJobDto } from "@/types/api";
import type { PersistedLabJobRecord } from "@/features/lab/lib/lab-job-persistence";

function activeJob(partial: Partial<ActiveLabJobDto> & Pick<ActiveLabJobDto, "jobId" | "benchmarkKind">): ActiveLabJobDto {
  const jid = partial.jobId;
  return {
    jobId: jid,
    benchmarkKind: partial.benchmarkKind,
    evaluationRunId: partial.evaluationRunId ?? "550e8400-e29b-41d4-a716-446655440001",
    projectId: partial.projectId ?? null,
    datasetId: partial.datasetId ?? null,
    status: partial.status ?? "RUNNING",
    progress: partial.progress ?? null,
    startedAt: partial.startedAt ?? "2024-01-02T00:00:00.000Z",
    updatedAt: partial.updatedAt ?? "2024-01-02T00:01:00.000Z",
    pollPath: partial.pollPath ?? `/lab/jobs/${jid}`,
    streamPath: partial.streamPath ?? `/lab/jobs/${jid}/events`,
    cancellable: partial.cancellable ?? true,
  };
}

function sessionRec(partial: Partial<PersistedLabJobRecord> & Pick<PersistedLabJobRecord, "jobId" | "sectionKey">): PersistedLabJobRecord {
  const jid = partial.jobId;
  return {
    jobId: jid,
    sectionKey: partial.sectionKey,
    accepted: partial.accepted ?? {
      jobId: jid,
      status: "RUNNING",
      pollPath: `/lab/jobs/${jid}`,
      streamPath: `/lab/jobs/${jid}/events`,
    },
    evaluationRunId: partial.evaluationRunId ?? null,
    followMode: partial.followMode ?? "poll",
    startedAtMs: partial.startedAtMs ?? 1,
    lastUpdatedMs: partial.lastUpdatedMs ?? 2,
    lastStatus: partial.lastStatus ?? {
      id: jid,
      taskType: "LAB",
      status: "RUNNING",
      terminal: false,
      progressText: null,
      errorMessage: null,
      failureCode: null,
      result: null,
    },
    stoppedWatching: partial.stoppedWatching ?? false,
    staleNotFound: partial.staleNotFound ?? false,
    pollTimedOut: partial.pollTimedOut ?? false,
    dismissedTerminal: partial.dismissedTerminal ?? false,
  };
}

describe("computeLabActiveJobRecovery", () => {
  it("returns none when sectionKey and benchmarkKind are inconsistent", () => {
    const d = computeLabActiveJobRecovery({
      sectionKey: "evaluation-rag",
      benchmarkKind: "LLM_JUDGE_QA",
      activeProjectId: null,
      draftFollowMode: null,
      backendActiveJobs: [activeJob({ jobId: "a", benchmarkKind: "LLM_JUDGE_QA" })],
      backendActiveJobsLoading: false,
      backendActiveJobsError: null,
      sessionRecords: [],
    });
    expect(d.kind).toBe("none");
  });

  it("returns none while backend active jobs are loading", () => {
    const d = computeLabActiveJobRecovery({
      sectionKey: "evaluation-llm",
      benchmarkKind: "LLM_JUDGE_QA",
      activeProjectId: null,
      draftFollowMode: null,
      backendActiveJobs: [],
      backendActiveJobsLoading: true,
      backendActiveJobsError: null,
      sessionRecords: [],
    });
    expect(d.kind).toBe("none");
  });

  it("returns auto_follow for a single matching backend job (empty session)", () => {
    const job = activeJob({ jobId: "job-1", benchmarkKind: "LLM_JUDGE_QA", projectId: null });
    const d = computeLabActiveJobRecovery({
      sectionKey: "evaluation-llm",
      benchmarkKind: "LLM_JUDGE_QA",
      activeProjectId: null,
      draftFollowMode: "sse",
      backendActiveJobs: [job],
      backendActiveJobsLoading: false,
      backendActiveJobsError: null,
      sessionRecords: [],
    });
    expect(d.kind).toBe("auto_follow");
    if (d.kind === "auto_follow") {
      expect(d.candidate.jobId).toBe("job-1");
      expect(d.candidate.resolvedFollowMode).toBe("sse");
      expect(d.candidate.accepted.jobId).toBe("job-1");
    }
  });

  it("defaults follow mode to poll when draft has no preference", () => {
    const job = activeJob({ jobId: "job-2", benchmarkKind: "EMBEDDING_RETRIEVAL" });
    const d = computeLabActiveJobRecovery({
      sectionKey: "evaluation-embedding",
      benchmarkKind: "EMBEDDING_RETRIEVAL",
      activeProjectId: null,
      draftFollowMode: null,
      backendActiveJobs: [job],
      backendActiveJobsLoading: false,
      backendActiveJobsError: null,
      sessionRecords: [],
    });
    expect(d.kind).toBe("auto_follow");
    if (d.kind === "auto_follow") {
      expect(d.candidate.resolvedFollowMode).toBe("poll");
    }
  });

  it("returns session_only when no backend job matches but session has non-terminal row", () => {
    const rec = sessionRec({ jobId: "sess-1", sectionKey: "evaluation-llm" });
    const d = computeLabActiveJobRecovery({
      sectionKey: "evaluation-llm",
      benchmarkKind: "LLM_JUDGE_QA",
      activeProjectId: null,
      draftFollowMode: null,
      backendActiveJobs: [],
      backendActiveJobsLoading: false,
      backendActiveJobsError: null,
      sessionRecords: [rec],
    });
    expect(d.kind).toBe("session_only");
    if (d.kind === "session_only") {
      expect(d.record.jobId).toBe("sess-1");
    }
  });

  it("returns cta when two jobs share the same ordering key", () => {
    const t = "2024-01-02T00:00:00.000Z";
    const a = activeJob({ jobId: "a", benchmarkKind: "LLM_JUDGE_QA", startedAt: t, updatedAt: t });
    const b = activeJob({ jobId: "b", benchmarkKind: "LLM_JUDGE_QA", startedAt: t, updatedAt: t });
    const d = computeLabActiveJobRecovery({
      sectionKey: "evaluation-llm",
      benchmarkKind: "LLM_JUDGE_QA",
      activeProjectId: null,
      draftFollowMode: null,
      backendActiveJobs: [a, b],
      backendActiveJobsLoading: false,
      backendActiveJobsError: null,
      sessionRecords: [],
    });
    expect(d.kind).toBe("cta");
    if (d.kind === "cta") {
      expect(d.candidates.length).toBe(2);
    }
  });

  it("returns auto_follow for the unique most-recent job when ordering keys differ", () => {
    const older = activeJob({
      jobId: "old",
      benchmarkKind: "RAG_PRESET_END_TO_END",
      startedAt: "2024-01-01T00:00:00.000Z",
      updatedAt: "2024-01-01T00:00:00.000Z",
    });
    const newer = activeJob({
      jobId: "new",
      benchmarkKind: "RAG_PRESET_END_TO_END",
      startedAt: "2024-01-03T00:00:00.000Z",
      updatedAt: "2024-01-03T00:00:00.000Z",
    });
    const d = computeLabActiveJobRecovery({
      sectionKey: "evaluation-rag",
      benchmarkKind: "RAG_PRESET_END_TO_END",
      activeProjectId: null,
      draftFollowMode: null,
      backendActiveJobs: [older, newer],
      backendActiveJobsLoading: false,
      backendActiveJobsError: null,
      sessionRecords: [],
    });
    expect(d.kind).toBe("auto_follow");
    if (d.kind === "auto_follow") {
      expect(d.candidate.jobId).toBe("new");
    }
  });
});

describe("activeJobMatchesCard", () => {
  it("matches benchmark kind case-insensitively and both-null project scope", () => {
    const j = activeJob({ jobId: "x", benchmarkKind: "llm_judge_qa", projectId: null });
    expect(activeJobMatchesCard(j, "LLM_JUDGE_QA", null)).toBe(true);
  });

  it("rejects project-scoped job when card has no project", () => {
    const j = activeJob({ jobId: "x", benchmarkKind: "LLM_JUDGE_QA", projectId: "550e8400-e29b-41d4-a716-446655440099" });
    expect(activeJobMatchesCard(j, "LLM_JUDGE_QA", null)).toBe(false);
  });

  it("rejects global job when card is project-scoped", () => {
    const j = activeJob({ jobId: "x", benchmarkKind: "LLM_JUDGE_QA", projectId: null });
    expect(activeJobMatchesCard(j, "LLM_JUDGE_QA", "550e8400-e29b-41d4-a716-446655440099")).toBe(false);
  });
});

describe("isSectionBenchmarkConsistent", () => {
  it("is true for evaluation-llm + LLM_JUDGE_QA", () => {
    expect(isSectionBenchmarkConsistent("evaluation-llm", "LLM_JUDGE_QA")).toBe(true);
  });
});

describe("orderingKeyForActiveJob", () => {
  it("is stable for identical timestamps", () => {
    const t = "2024-05-01T12:00:00.000Z";
    const a = activeJob({ jobId: "a", benchmarkKind: "LLM_JUDGE_QA", startedAt: t, updatedAt: t });
    const b = activeJob({ jobId: "b", benchmarkKind: "LLM_JUDGE_QA", startedAt: t, updatedAt: t });
    expect(orderingKeyForActiveJob(a)).toBe(orderingKeyForActiveJob(b));
  });
});
