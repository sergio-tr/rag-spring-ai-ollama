import { describe, it, expect, beforeEach } from "vitest";
import type { AsyncTaskStatusDto } from "@/types/api";
import { useTraceStore } from "@/features/trace/trace.store";
import {
  createLabJobTraceDedupe,
  emitLabJobTraceForTick,
  traceLabJobQueued,
  traceLabJobStoppedWaiting,
} from "./lab-job-trace";

function task(partial: Partial<AsyncTaskStatusDto> & Pick<AsyncTaskStatusDto, "status" | "terminal">): AsyncTaskStatusDto {
  return {
    id: "1",
    taskType: "LAB",
    progressText: null,
    result: null,
    errorMessage: null,
    createdAt: "",
    updatedAt: "",
    startedAt: null,
    completedAt: null,
    ...partial,
  };
}

const messages = {
  queued: "q",
  running: "r",
  completed: "c",
  failed: "f",
  cancelled: "x",
};

describe("lab-job-trace", () => {
  beforeEach(() => {
    useTraceStore.getState().clearTraceEvents();
  });

  it("traceLabJobQueued appends lab section event", () => {
    traceLabJobQueued("jid", "hello");
    const ev = useTraceStore.getState().events;
    expect(ev.some((e) => e.section === "lab" && e.action === "lab_job_queued" && e.metadata?.jobId === "jid")).toBe(
      true,
    );
  });

  it("traceLabJobStoppedWaiting uses warning status", () => {
    traceLabJobStoppedWaiting("jid", "stopped");
    const e = useTraceStore.getState().events.find((x) => x.action === "lab_job_stopped_waiting");
    expect(e?.status).toBe("warning");
  });

  it("emitLabJobTraceForTick emits running once", () => {
    const d = createLabJobTraceDedupe();
    emitLabJobTraceForTick(d, task({ status: "RUNNING", terminal: false }), "j1", messages);
    emitLabJobTraceForTick(d, task({ status: "RUNNING", terminal: false }), "j1", messages);
    expect(useTraceStore.getState().events.filter((e) => e.action === "lab_job_running")).toHaveLength(1);
  });

  it("emitLabJobTraceForTick emits terminal once", () => {
    const d = createLabJobTraceDedupe();
    emitLabJobTraceForTick(d, task({ status: "SUCCEEDED", terminal: true }), "j1", messages);
    emitLabJobTraceForTick(d, task({ status: "SUCCEEDED", terminal: true }), "j1", messages);
    expect(useTraceStore.getState().events.filter((e) => e.action === "lab_job_completed")).toHaveLength(1);
  });
});
