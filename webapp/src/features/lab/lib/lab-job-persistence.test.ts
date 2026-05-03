import { describe, it, expect } from "vitest";
import type { AsyncTaskStatusDto, LabJobAcceptedDto } from "@/types/api";
import {
  MAX_LAB_JOB_RECORDS,
  asyncTaskDtoFromSnapshot,
  initialSnapshotFromAccepted,
  labSectionHref,
  pathnameMatchesLabSection,
  pickLatestRecordForSection,
  pickPrimaryLabBannerRecord,
  snapshotFromAsyncTask,
  trimLabJobRecords,
  upsertLabJobRecordList,
  type PersistedLabJobRecord,
} from "./lab-job-persistence";

function accepted(jobId: string): LabJobAcceptedDto {
  return {
    jobId,
    status: "QUEUED",
    pollPath: `/lab/jobs/${jobId}`,
    streamPath: `/lab/jobs/${jobId}/events`,
  };
}

function record(id: string, lastUpdatedMs: number): PersistedLabJobRecord {
  const acc = accepted(id);
  const snap = initialSnapshotFromAccepted(acc, "LAB");
  return {
    jobId: id,
    sectionKey: "evaluation-llm",
    accepted: acc,
    followMode: "poll",
    startedAtMs: lastUpdatedMs,
    lastUpdatedMs,
    lastStatus: snap,
    stoppedWatching: false,
    staleNotFound: false,
    pollTimedOut: false,
    dismissedTerminal: false,
  };
}

describe("lab-job-persistence", () => {
  it("labSectionHref maps section keys to product routes", () => {
    expect(labSectionHref("classifier-train")).toBe("/lab/classifier");
    expect(labSectionHref("classifier-eval")).toBe("/lab/classifier");
    expect(labSectionHref("evaluation-llm")).toBe("/lab/evaluation/llm");
    expect(labSectionHref("evaluation-rag")).toBe("/lab/evaluation/rag");
  });

  it("pathnameMatchesLabSection handles null and localized-ish paths", () => {
    expect(pathnameMatchesLabSection(null, "evaluation-rag")).toBe(false);
    expect(pathnameMatchesLabSection("/en/lab/evaluation/rag", "evaluation-rag")).toBe(true);
    expect(pathnameMatchesLabSection("/lab/classifier", "classifier-train")).toBe(true);
    expect(pathnameMatchesLabSection("/lab/evaluation/llm", "evaluation-llm")).toBe(true);
    expect(pathnameMatchesLabSection("/lab/other", "evaluation-rag")).toBe(false);
  });

  it("snapshotFromAsyncTask copies stable fields", () => {
    const dto: AsyncTaskStatusDto = {
      id: "job-1",
      taskType: "EVAL",
      status: "RUNNING",
      progressText: "p",
      result: { x: 1 },
      errorMessage: null,
      terminal: false,
      createdAt: "",
      updatedAt: "",
      startedAt: null,
      completedAt: null,
      failureCode: undefined,
    };
    expect(snapshotFromAsyncTask(dto)).toEqual({
      id: "job-1",
      taskType: "EVAL",
      status: "RUNNING",
      terminal: false,
      progressText: "p",
      errorMessage: null,
      failureCode: null,
      result: { x: 1 },
    });
  });

  it("asyncTaskDtoFromSnapshot returns null for missing snapshot", () => {
    expect(asyncTaskDtoFromSnapshot("j", null)).toBeNull();
  });

  it("asyncTaskDtoFromSnapshot fills job id when snapshot id empty", () => {
    const snap = initialSnapshotFromAccepted(accepted("fallback-id"), "LAB");
    const dto = asyncTaskDtoFromSnapshot("fallback-id", { ...snap, id: "" });
    expect(dto?.id).toBe("fallback-id");
    expect(dto?.createdAt).toBe("");
    expect(dto?.failureCode).toBeNull();
  });

  it("pickLatestRecordForSection chooses newest by lastUpdatedMs", () => {
    const older = record("a", 10);
    const newer = { ...record("b", 99), sectionKey: "evaluation-llm" as const };
    const otherSection = { ...record("c", 999), sectionKey: "evaluation-rag" as const };
    expect(pickLatestRecordForSection([older, newer, otherSection], "evaluation-llm")).toEqual(newer);
    expect(pickLatestRecordForSection([otherSection], "evaluation-llm")).toBeNull();
  });

  it("pickPrimaryLabBannerRecord prefers stopped-watching non-terminal over in-flight", () => {
    const inflight = record("i", 500);
    const stopped = { ...record("s", 100), stoppedWatching: true };
    expect(pickPrimaryLabBannerRecord([inflight, stopped])?.jobId).toBe("s");
  });

  it("pickPrimaryLabBannerRecord falls back to newest terminal job", () => {
    const snap = {
      ...initialSnapshotFromAccepted(accepted("done"), "LAB"),
      terminal: true,
      status: "SUCCEEDED",
    };
    const terminal = { ...record("done", 20), lastStatus: snap };
    expect(pickPrimaryLabBannerRecord([terminal])?.jobId).toBe("done");
  });

  it("trimLabJobRecords keeps the newest jobs up to MAX_LAB_JOB_RECORDS", () => {
    const rows = Array.from({ length: MAX_LAB_JOB_RECORDS + 3 }, (_, i) => record(`j${i}`, 1000 + i));
    const trimmed = trimLabJobRecords(rows);
    expect(trimmed).toHaveLength(MAX_LAB_JOB_RECORDS);
    expect(new Set(trimmed.map((r) => r.jobId))).toEqual(new Set(["j3", "j4", "j5", "j6", "j7"]));
  });

  it("upsertLabJobRecordList replaces same jobId and trims", () => {
    let rows: PersistedLabJobRecord[] = [];
    for (let i = 0; i < MAX_LAB_JOB_RECORDS; i++) {
      rows = upsertLabJobRecordList(rows, record(`x${i}`, i));
    }
    rows = upsertLabJobRecordList(rows, record("x2", 9999));
    expect(rows.find((r) => r.jobId === "x2")?.lastUpdatedMs).toBe(9999);
    expect(rows).toHaveLength(MAX_LAB_JOB_RECORDS);
  });

  it("pickPrimaryLabBannerRecord prefers stale ahead of in-flight", () => {
    const stale = {
      ...record("s", 1),
      staleNotFound: true,
      lastUpdatedMs: 50,
    };
    const inflight = { ...record("i", 2), lastUpdatedMs: 900 };
    expect(pickPrimaryLabBannerRecord([inflight, stale])?.jobId).toBe("s");
  });

  it("pickPrimaryLabBannerRecord hides dismissed terminal rows", () => {
    const snap = {
      ...initialSnapshotFromAccepted(accepted("t"), "LAB"),
      terminal: true,
      status: "SUCCEEDED",
    };
    const dismissed: PersistedLabJobRecord = {
      ...record("t", 10),
      lastStatus: snap,
      dismissedTerminal: true,
    };
    expect(pickPrimaryLabBannerRecord([dismissed])).toBeNull();
  });
});
