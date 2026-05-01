import { describe, expect, it } from "vitest";
import type { AsyncTaskStatusDto } from "@/types/api";
import { getLabJobUiPhase, labPhaseToTraceStatus } from "./lab-task-ui";

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

describe("getLabJobUiPhase", () => {
  it("returns queued when waiting for first tick", () => {
    expect(getLabJobUiPhase({ taskStatus: null, queuedHint: true, stoppedWaiting: false })).toBe("queued");
  });

  it("prefers stopped_waiting over task status", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: task({ status: "RUNNING", terminal: false }),
        queuedHint: false,
        stoppedWaiting: true,
      }),
    ).toBe("stopped_waiting");
  });

  it("maps terminal SUCCEEDED", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: task({ status: "SUCCEEDED", terminal: true }),
        queuedHint: false,
        stoppedWaiting: false,
      }),
    ).toBe("completed");
  });

  it("maps terminal FAILED", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: task({ status: "FAILED", terminal: true }),
        queuedHint: false,
        stoppedWaiting: false,
      }),
    ).toBe("failed");
  });

  it("maps terminal CANCELLED", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: task({ status: "CANCELLED", terminal: true }),
        queuedHint: false,
        stoppedWaiting: false,
      }),
    ).toBe("cancelled");
  });

  it("maps non-terminal RUNNING", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: task({ status: "RUNNING", terminal: false }),
        queuedHint: false,
        stoppedWaiting: false,
      }),
    ).toBe("running");
  });

  it("maps non-terminal QUEUED", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: task({ status: "QUEUED", terminal: false }),
        queuedHint: false,
        stoppedWaiting: false,
      }),
    ).toBe("queued");
  });
});

describe("labPhaseToTraceStatus", () => {
  it("maps completed to success", () => {
    expect(labPhaseToTraceStatus("completed")).toBe("success");
  });

  it("maps stopped_waiting to warning", () => {
    expect(labPhaseToTraceStatus("stopped_waiting")).toBe("warning");
  });
});
