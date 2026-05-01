import { describe, it, expect } from "vitest";
import type { LabJobAcceptedDto } from "@/types/api";
import {
  MAX_LAB_JOB_RECORDS,
  initialSnapshotFromAccepted,
  pickPrimaryLabBannerRecord,
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
