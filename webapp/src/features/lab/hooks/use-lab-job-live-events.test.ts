import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import {
  activeJobMatchesCard,
  computeLabActiveJobRecovery,
} from "./use-lab-active-job-recovery";
import { useLabJobLiveEvents } from "./use-lab-job-live-events";
import * as asyncTask from "@/lib/async-task";
import * as labJobSse from "@/lib/lab-job-sse";
import type { LabJobStreamCallbacks } from "@/lib/lab-job-sse";
import type { ActiveLabJobDto, LabJobAcceptedDto } from "@/types/api";

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

const terminalStatus = {
  id: "job-live-1",
  taskType: "LAB",
  status: "SUCCEEDED",
  terminal: true,
  progressText: null,
  result: null,
  errorMessage: null,
  createdAt: "t",
  updatedAt: "t",
  startedAt: null,
  completedAt: "t",
  failureCode: null,
};

const accepted: LabJobAcceptedDto = {
  jobId: "job-live-1",
  status: "ACCEPTED",
  pollPath: "/lab/jobs/job-live-1",
  streamPath: "/lab/jobs/job-live-1/events",
};

describe("useLabJobLiveEvents", () => {
  beforeEach(() => {
    vi.spyOn(asyncTask, "fetchLabJobStatusOnce").mockResolvedValue({
      ...terminalStatus,
      terminal: false,
      status: "RUNNING",
    });
    vi.spyOn(labJobSse, "streamLabJobLive").mockResolvedValue(terminalStatus);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("stays idle when job id or stream path is blank", () => {
    const { result: noJob } = renderHook(() =>
      useLabJobLiveEvents({ accepted: { ...accepted, jobId: "  " } }),
    );
    expect(noJob.current.connectionState).toBe("idle");

    const { result: noStream } = renderHook(() =>
      useLabJobLiveEvents({ accepted: { ...accepted, streamPath: "" } }),
    );
    expect(noStream.current.connectionState).toBe("idle");
  });

  it("maps unknown terminal snapshot status to failed", async () => {
    vi.mocked(asyncTask.fetchLabJobStatusOnce).mockResolvedValue({
      ...terminalStatus,
      terminal: true,
      status: "WEIRD",
    });
    const { result } = renderHook(() => useLabJobLiveEvents({ accepted }));
    await waitFor(() => expect(result.current.connectionState).toBe("failed"));
  });

  it("sets idle when the stream aborts", async () => {
    vi.spyOn(labJobSse, "streamLabJobLive").mockRejectedValue(new DOMException("Aborted", "AbortError"));
    const { result } = renderHook(() => useLabJobLiveEvents({ accepted }));
    await waitFor(() => expect(result.current.connectionState).toBe("idle"));
  });

  it("reports idle when disabled", () => {
    const { result } = renderHook(() =>
      useLabJobLiveEvents({ accepted, enabled: false }),
    );
    expect(result.current.connectionState).toBe("idle");
  });

  it("completes via live stream when enabled", async () => {
    const onTerminal = vi.fn();
    const { result } = renderHook(() =>
      useLabJobLiveEvents({ accepted, onTerminal }),
    );

    await waitFor(() => expect(result.current.connectionState).toBe("completed"));
    expect(result.current.taskStatus?.status).toBe("SUCCEEDED");
    expect(onTerminal).toHaveBeenCalled();
  });

  it("short-circuits when snapshot is already terminal", async () => {
    vi.mocked(asyncTask.fetchLabJobStatusOnce).mockResolvedValue(terminalStatus);
    const onTerminal = vi.fn();
    const { result } = renderHook(() =>
      useLabJobLiveEvents({ accepted, onTerminal }),
    );

    await waitFor(() => expect(result.current.connectionState).toBe("completed"));
    expect(labJobSse.streamLabJobLive).not.toHaveBeenCalled();
    expect(onTerminal).toHaveBeenCalled();
  });

  it("maps failed terminal snapshot without opening SSE", async () => {
    vi.mocked(asyncTask.fetchLabJobStatusOnce).mockResolvedValue({
      ...terminalStatus,
      status: "FAILED",
    });
    const { result } = renderHook(() => useLabJobLiveEvents({ accepted }));

    await waitFor(() => expect(result.current.connectionState).toBe("failed"));
  });

  it("maps cancelled terminal snapshot", async () => {
    vi.mocked(asyncTask.fetchLabJobStatusOnce).mockResolvedValue({
      ...terminalStatus,
      status: "CANCELLED",
    });
    const { result } = renderHook(() => useLabJobLiveEvents({ accepted }));

    await waitFor(() => expect(result.current.connectionState).toBe("cancelled"));
  });

  it("maps CANCELED spelling on stream terminal status", async () => {
    vi.spyOn(labJobSse, "streamLabJobLive").mockResolvedValue({
      ...terminalStatus,
      status: "CANCELED",
    });
    const { result } = renderHook(() => useLabJobLiveEvents({ accepted }));
    await waitFor(() => expect(result.current.connectionState).toBe("cancelled"));
  });

  it("maps onReconnecting callback to reconnecting state", async () => {
    let streamCallbacks: LabJobStreamCallbacks | undefined;
    let releaseStream: (() => void) | undefined;
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation((_path, options) => {
      streamCallbacks = options?.callbacks;
      return new Promise((resolve) => {
        releaseStream = () => resolve(terminalStatus);
      });
    });
    const { result, unmount } = renderHook(() => useLabJobLiveEvents({ accepted }));
    await waitFor(() => expect(streamCallbacks).toBeDefined());
    act(() => {
      streamCallbacks?.onReconnecting?.();
    });
    expect(result.current.connectionState).toBe("reconnecting");
    releaseStream?.();
    unmount();
  });

  it("resume retriggers stream subscription", async () => {
    const { result } = renderHook(() => useLabJobLiveEvents({ accepted }));
    await waitFor(() => expect(result.current.connectionState).toBe("completed"));

    result.current.resume();
    await waitFor(() => expect(labJobSse.streamLabJobLive).toHaveBeenCalledTimes(2));
  });

  it("forwards onTick while the stream is running", async () => {
    const onTick = vi.fn();
    renderHook(() => useLabJobLiveEvents({ accepted, onTick }));
    await waitFor(() => expect(onTick).toHaveBeenCalled());
  });

  it("surfaces reconnecting after stream errors", async () => {
    vi.spyOn(labJobSse, "streamLabJobLive").mockRejectedValue(new Error("stream down"));
    vi.mocked(asyncTask.fetchLabJobStatusOnce).mockResolvedValue({
      ...terminalStatus,
      terminal: false,
      status: "RUNNING",
    });
    const onStreamError = vi.fn();
    const { result } = renderHook(() =>
      useLabJobLiveEvents({ accepted, onStreamError }),
    );

    await waitFor(() => expect(result.current.connectionState).toBe("reconnecting"));
    expect(onStreamError).toHaveBeenCalled();
  });

  it("maps onConnecting to connecting before first live tick", async () => {
    let streamCallbacks: LabJobStreamCallbacks | undefined;
    let releaseStream: (() => void) | undefined;
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation((_path, options) => {
      streamCallbacks = options?.callbacks;
      return new Promise((resolve) => {
        releaseStream = () => resolve(terminalStatus);
      });
    });

    const { result, unmount } = renderHook(() => useLabJobLiveEvents({ accepted }));
    await waitFor(() => expect(streamCallbacks).toBeDefined());
    act(() => {
      streamCallbacks?.onConnecting?.();
    });
    expect(result.current.connectionState).toBe("connecting");
    releaseStream?.();
    unmount();
  });

  it("ignores job events without a positive event id", async () => {
    let streamCallbacks: LabJobStreamCallbacks | undefined;
    let releaseStream: (() => void) | undefined;
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation((_path, options) => {
      streamCallbacks = options?.callbacks;
      return new Promise((resolve) => {
        releaseStream = () => resolve(terminalStatus);
      });
    });

    const { result, unmount } = renderHook(() => useLabJobLiveEvents({ accepted }));
    await waitFor(() => expect(streamCallbacks).toBeDefined());
    act(() => {
      streamCallbacks?.onJobEvent?.({
        eventId: 0,
        jobId: "job-live-1",
        type: "HEARTBEAT",
        status: null,
        progress: null,
        message: null,
        timestamp: "t",
        payload: null,
      });
    });
    expect(result.current.lastEventId).toBeNull();
    releaseStream?.();
    unmount();
  });

  it("maps onConnecting to reconnecting when stream was live without recent events", async () => {
    let streamCallbacks: LabJobStreamCallbacks | undefined;
    let releaseStream: (() => void) | undefined;
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation((_path, options) => {
      streamCallbacks = options?.callbacks;
      return new Promise((resolve) => {
        releaseStream = () => resolve(terminalStatus);
      });
    });

    const { result, unmount } = renderHook(() => useLabJobLiveEvents({ accepted }));
    await waitFor(() => expect(streamCallbacks).toBeDefined());
    act(() => {
      streamCallbacks?.onLive?.();
    });
    await waitFor(() => expect(result.current.connectionState).toBe("live"));
    act(() => {
      streamCallbacks?.onConnecting?.();
    });
    expect(result.current.connectionState).toBe("reconnecting");
    releaseStream?.();
    unmount();
  });

  it("stays live on reconnecting callbacks when events arrived recently", async () => {
    let streamCallbacks: LabJobStreamCallbacks | undefined;
    let releaseStream: (() => void) | undefined;
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation((_path, options) => {
      streamCallbacks = options?.callbacks;
      return new Promise((resolve) => {
        releaseStream = () => resolve(terminalStatus);
      });
    });

    const { result, unmount } = renderHook(() => useLabJobLiveEvents({ accepted }));
    await waitFor(() => expect(streamCallbacks).toBeDefined());
    act(() => {
      streamCallbacks?.onLive?.();
      streamCallbacks?.onTaskTick?.({
        ...terminalStatus,
        terminal: false,
        status: "RUNNING",
      });
      streamCallbacks?.onReconnecting?.();
      streamCallbacks?.onConnecting?.();
    });
    expect(result.current.connectionState).toBe("live");
    releaseStream?.();
    unmount();
  });

  it("maps stream callbacks to connection state and lastEventId", async () => {
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation(async (_path, options) => {
      const cbs = options?.callbacks;
      cbs?.onConnecting?.();
      cbs?.onLive?.();
      cbs?.onResumed?.();
      cbs?.onReconnecting?.();
      cbs?.onJobEvent?.({
        eventId: 4,
        jobId: "job-live-1",
        type: "PROGRESS",
        status: "RUNNING",
        progress: "half",
        message: null,
        timestamp: "t4",
        payload: {},
      });
      cbs?.onTaskTick?.({
        ...terminalStatus,
        terminal: false,
        status: "RUNNING",
      });
      return { ...terminalStatus, status: "CANCELED" };
    });

    const { result } = renderHook(() => useLabJobLiveEvents({ accepted }));
    await waitFor(() => expect(result.current.connectionState).toBe("cancelled"));
    expect(result.current.lastEventId).toBe(4);
  });

  it("flashes resumed then returns to live", async () => {
    let streamCallbacks: LabJobStreamCallbacks | undefined;
    let releaseStream: (() => void) | undefined;
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation((_path, options) => {
      streamCallbacks = options?.callbacks;
      return new Promise((resolve) => {
        releaseStream = () => resolve(terminalStatus);
      });
    });

    const { result, unmount } = renderHook(() => useLabJobLiveEvents({ accepted }));
    await waitFor(() => expect(streamCallbacks).toBeDefined());

    vi.useFakeTimers();
    try {
      act(() => {
        streamCallbacks?.onResumed?.();
      });
      expect(result.current.connectionState).toBe("resumed");

      act(() => {
        vi.advanceTimersByTime(2_500);
      });
      expect(result.current.connectionState).toBe("live");
    } finally {
      vi.useRealTimers();
      releaseStream?.();
      unmount();
    }
  });

  it("coerces legacy poll followMode drafts to sse-only recovery", () => {
    const decision = computeLabActiveJobRecovery({
      sectionKey: "evaluation-llm",
      benchmarkKind: "LLM_JUDGE_QA",
      activeProjectId: null,
      draftFollowMode: "poll",
      backendActiveJobs: [],
      backendActiveJobsLoading: false,
      backendActiveJobsError: null,
      sessionRecords: [],
    });
    expect(decision.kind).toBe("none");
  });
});
