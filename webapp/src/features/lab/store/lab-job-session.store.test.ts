import { describe, it, expect, beforeEach } from "vitest";
import type { LabJobAcceptedDto } from "@/types/api";
import { useLabJobSessionStore } from "./lab-job-session.store";

function acc(jobId: string): LabJobAcceptedDto {
  return {
    jobId,
    status: "QUEUED",
    pollPath: `/lab/jobs/${jobId}`,
    streamPath: `/lab/jobs/${jobId}/events`,
  };
}

describe("useLabJobSessionStore", () => {
  beforeEach(() => {
    sessionStorage.removeItem("rag-lab-jobs");
    useLabJobSessionStore.persist.clearStorage();
    useLabJobSessionStore.setState({ records: [], pendingResume: null, resumeNonce: 0 });
  });

  it("stores metadata when a lab job is accepted", () => {
    const acceptedDto = acc("job-a");
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: acceptedDto,
      sectionKey: "classifier-eval",
      followMode: "poll",
      taskTypeHint: "EVAL",
    });
    const rec = useLabJobSessionStore.getState().records.find((r) => r.jobId === "job-a");
    expect(rec?.sectionKey).toBe("classifier-eval");
    expect(rec?.accepted.pollPath).toContain("job-a");
    expect(rec?.followMode).toBe("poll");
    expect(rec?.startedAtMs).toBeGreaterThan(0);
  });

  it("updates last known status from polling ticks", () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: acc("job-b"),
      sectionKey: "evaluation-llm",
      followMode: "sse",
    });
    useLabJobSessionStore.getState().patchLabJobFromTick("job-b", {
      id: "job-b",
      taskType: "LAB",
      status: "RUNNING",
      progressText: null,
      result: null,
      errorMessage: null,
      terminal: false,
      createdAt: "",
      updatedAt: "",
      startedAt: null,
      completedAt: null,
    });
    const snap = useLabJobSessionStore.getState().records.find((r) => r.jobId === "job-b")?.lastStatus;
    expect(snap?.status).toBe("RUNNING");
    expect(snap?.terminal).toBe(false);
  });

  it("requestResume + consume hands off to panels via pending resume", () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: acc("job-c"),
      sectionKey: "evaluation-rag",
      followMode: "poll",
    });
    const before = useLabJobSessionStore.getState().resumeNonce;
    useLabJobSessionStore.getState().requestResumeLabJob("evaluation-rag", "job-c");
    expect(useLabJobSessionStore.getState().resumeNonce).toBe(before + 1);
    const consumed = useLabJobSessionStore.getState().consumePendingResume("evaluation-rag");
    expect(consumed?.jobId).toBe("job-c");
    expect(useLabJobSessionStore.getState().consumePendingResume("evaluation-rag")).toBeNull();
  });

  it("marks stale-not-found without dropping the record", () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: acc("job-d"),
      sectionKey: "classifier-train",
      followMode: "poll",
    });
    useLabJobSessionStore.getState().markLabJobStaleNotFound("job-d");
    expect(useLabJobSessionStore.getState().records.find((r) => r.jobId === "job-d")?.staleNotFound).toBe(true);
  });

  it("caps record growth via upsertLabJobOnAccepted", () => {
    for (let i = 0; i < 7; i++) {
      useLabJobSessionStore.getState().upsertLabJobOnAccepted({
        accepted: acc(`job-${i}`),
        sectionKey: "evaluation-llm",
        followMode: "poll",
      });
    }
    expect(useLabJobSessionStore.getState().records.length).toBeLessThanOrEqual(5);
  });

  it("stores evaluationRunId when typed benchmark accepts", () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: acc("bench-1"),
      sectionKey: "evaluation-embedding",
      followMode: "poll",
      evaluationRunId: "550e8400-e29b-41d4-a716-446655440099",
    });
    expect(useLabJobSessionStore.getState().records.find((r) => r.jobId === "bench-1")?.evaluationRunId).toBe(
      "550e8400-e29b-41d4-a716-446655440099",
    );
  });

  it("consumePendingResume returns null on section mismatch without clearing pending resume", () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: acc("job-wrong-sec"),
      sectionKey: "evaluation-llm",
      followMode: "poll",
    });
    useLabJobSessionStore.getState().requestResumeLabJob("evaluation-llm", "job-wrong-sec");
    expect(useLabJobSessionStore.getState().consumePendingResume("evaluation-rag")).toBeNull();
    expect(useLabJobSessionStore.getState().pendingResume?.jobId).toBe("job-wrong-sec");
  });

  it("dismissTerminalLabJob marks dismissedTerminal", () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: acc("job-dismiss"),
      sectionKey: "evaluation-llm",
      followMode: "poll",
    });
    useLabJobSessionStore.getState().dismissTerminalLabJob("job-dismiss");
    expect(useLabJobSessionStore.getState().records.find((r) => r.jobId === "job-dismiss")?.dismissedTerminal).toBe(
      true,
    );
  });

  it("clearLabJobRecord drops pending resume when it targets the same job id", () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: acc("job-clear"),
      sectionKey: "evaluation-llm",
      followMode: "poll",
    });
    useLabJobSessionStore.getState().requestResumeLabJob("evaluation-llm", "job-clear");
    useLabJobSessionStore.getState().clearLabJobRecord("job-clear");
    expect(useLabJobSessionStore.getState().pendingResume).toBeNull();
    expect(useLabJobSessionStore.getState().records.some((r) => r.jobId === "job-clear")).toBe(false);
  });

  it("clearOtherLabJobsForSection keeps only the retained job id for that section", () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: acc("keep-me"),
      sectionKey: "evaluation-llm",
      followMode: "poll",
    });
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: acc("drop-me"),
      sectionKey: "evaluation-llm",
      followMode: "poll",
    });
    useLabJobSessionStore.getState().clearOtherLabJobsForSection("evaluation-llm", "keep-me");
    const ids = useLabJobSessionStore.getState().records.filter((r) => r.sectionKey === "evaluation-llm").map((r) => r.jobId);
    expect(ids).toEqual(["keep-me"]);
  });

  it("patchLabJobPollTimedOut sets pollTimedOut flag", () => {
    useLabJobSessionStore.getState().upsertLabJobOnAccepted({
      accepted: acc("job-to"),
      sectionKey: "evaluation-llm",
      followMode: "poll",
    });
    useLabJobSessionStore.getState().patchLabJobPollTimedOut("job-to", null);
    expect(useLabJobSessionStore.getState().records.find((r) => r.jobId === "job-to")?.pollTimedOut).toBe(true);
  });
});
