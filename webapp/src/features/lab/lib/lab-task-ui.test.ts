import { describe, expect, it } from "vitest";
import type { AsyncTaskStatusDto } from "@/types/api";
import { getLabJobStatusLabel, getLabJobUiPhase, labPhaseToTraceStatus } from "./lab-task-ui";

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

  it("maps legacy stoppedWaiting flag to reconnecting phase", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: task({ status: "RUNNING", terminal: false }),
        queuedHint: false,
        stoppedWaiting: true,
      }),
    ).toBe("reconnecting");
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

  it("maps non-terminal CANCELLING", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: task({ status: "CANCELLING", terminal: false }),
        queuedHint: false,
        stoppedWaiting: false,
      }),
    ).toBe("cancelling");
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

  it("maps live connection without task as queued when hinted", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: null,
        queuedHint: true,
        connectionState: "live",
      }),
    ).toBe("queued");
  });

  it("maps live connection with unknown terminal status to failed", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: task({ status: "UNKNOWN", terminal: true }),
        connectionState: "live",
      }),
    ).toBe("failed");
  });

  it("maps live connection with terminal CANCELED spelling", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: task({ status: "CANCELED", terminal: true }),
        connectionState: "live",
      }),
    ).toBe("cancelled");
  });

  it("maps connectionState finished_away and terminal fallbacks", () => {
    expect(getLabJobUiPhase({ taskStatus: null, connectionState: "finished_away" })).toBe("finished_away");
    expect(getLabJobUiPhase({ taskStatus: null, connectionState: "completed" })).toBe("completed");
    expect(getLabJobUiPhase({ taskStatus: null, connectionState: "failed" })).toBe("failed");
    expect(getLabJobUiPhase({ taskStatus: null, connectionState: "cancelled" })).toBe("cancelled");
    expect(getLabJobUiPhase({ taskStatus: null, connectionState: "connecting" })).toBe("connecting");
    expect(getLabJobUiPhase({ taskStatus: null, connectionState: "resumed" })).toBe("resumed");
  });

  it("treats missing status string as unknown_running", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: { ...task({ status: "RUNNING", terminal: false }), status: undefined as unknown as string },
        queuedHint: false,
        stoppedWaiting: false,
      }),
    ).toBe("unknown_running");
  });

  it("maps unknown non-terminal status to unknown_running", () => {
    expect(
      getLabJobUiPhase({
        taskStatus: task({ status: "CUSTOM", terminal: false }),
        connectionState: "live",
      }),
    ).toBe("unknown_running");
  });
});

const labels = {
  connecting: "Connecting",
  live: "Live",
  reconnecting: "Reconnecting",
  resumed: "Resumed",
  finishedAway: "Finished away",
  queued: "Queued",
  running: "Running",
  cancelling: "Stopping",
  completed: "Completed",
  failed: "Failed",
  cancelled: "Cancelled",
  stoppedWaiting: "Stopped",
  unknownRunning: "Unknown",
  streamConfigurationError: "Live stream configuration error",
};

describe("getLabJobStatusLabel", () => {
  it("returns labels for each phase branch", () => {
    expect(getLabJobStatusLabel("connecting", labels)).toBe("Connecting");
    expect(getLabJobStatusLabel("live", labels)).toBe("Live");
    expect(getLabJobStatusLabel("reconnecting", labels)).toBe("Reconnecting");
    expect(getLabJobStatusLabel("stopped_waiting", labels)).toBe("Reconnecting");
    expect(getLabJobStatusLabel("resumed", labels)).toBe("Resumed");
    expect(getLabJobStatusLabel("finished_away", labels)).toBe("Finished away");
    expect(getLabJobStatusLabel("unknown_running", labels)).toBe("Unknown");
    expect(getLabJobStatusLabel("cancelling", labels)).toBe("Stopping");
    expect(getLabJobStatusLabel("idle", labels)).toBe("Queued");
    expect(getLabJobStatusLabel("failed", labels, "configuration_error")).toBe(
      "Live stream configuration error",
    );
  });
});

describe("labPhaseToTraceStatus", () => {
  it("maps completed to success", () => {
    expect(labPhaseToTraceStatus("completed")).toBe("success");
  });

  it("maps reconnecting to warning", () => {
    expect(labPhaseToTraceStatus("reconnecting")).toBe("warning");
  });

  it("maps remaining phases", () => {
    expect(labPhaseToTraceStatus("failed")).toBe("error");
    expect(labPhaseToTraceStatus("cancelled")).toBe("warning");
    expect(labPhaseToTraceStatus("finished_away")).toBe("warning");
    expect(labPhaseToTraceStatus("resumed")).toBe("warning");
    expect(labPhaseToTraceStatus("stopped_waiting")).toBe("warning");
    expect(labPhaseToTraceStatus("running")).toBe("in_progress");
    expect(labPhaseToTraceStatus("idle")).toBe("info");
  });
});
