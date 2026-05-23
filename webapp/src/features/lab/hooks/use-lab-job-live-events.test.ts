import { describe, it, expect } from "vitest";
import {
  activeJobMatchesCard,
  computeLabActiveJobRecovery,
} from "./use-lab-active-job-recovery";
import type { ActiveLabJobDto } from "@/types/api";

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

describe("useLabJobLiveEvents recovery matching", () => {
  it("matches lab-only jobs even when card has an active project", () => {
    const j = activeJob({ jobId: "x", benchmarkKind: "LLM_JUDGE_QA", projectId: null });
    expect(activeJobMatchesCard(j, "LLM_JUDGE_QA", "550e8400-e29b-41d4-a716-446655440099")).toBe(true);
  });

  it("defaults follow mode to sse when draft has no preference", () => {
    const job = activeJob({ jobId: "job-2", benchmarkKind: "EMBEDDING_RETRIEVAL" });
    const d = computeLabActiveJobRecovery({
      sectionKey: "evaluation-embedding",
      benchmarkKind: "EMBEDDING_RETRIEVAL",
      activeProjectId: "p1",
      draftFollowMode: null,
      backendActiveJobs: [job],
      backendActiveJobsLoading: false,
      backendActiveJobsError: null,
      sessionRecords: [],
    });
    expect(d.kind).toBe("auto_follow");
    if (d.kind === "auto_follow") {
      expect(d.candidate.resolvedFollowMode).toBe("sse");
    }
  });
});
