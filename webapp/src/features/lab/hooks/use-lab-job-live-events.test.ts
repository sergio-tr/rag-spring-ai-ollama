import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import {
  activeJobMatchesCard,
  computeLabActiveJobResumption,
} from "./use-lab-active-job-resumption";
import { useLabJobSse } from "./use-lab-job-sse";
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

describe("useLabJobSse recovery matching", () => {
  it("matches lab-only jobs even when card has an active project", () => {
    const j = activeJob({ jobId: "x", benchmarkKind: "LLM_JUDGE_QA", projectId: null });
    expect(activeJobMatchesCard(j, "LLM_JUDGE_QA", "550e8400-e29b-41d4-a716-446655440099")).toBe(true);
  });

  it("defaults follow mode to sse when draft has no preference", () => {
    const job = activeJob({ jobId: "job-2", benchmarkKind: "EMBEDDING_RETRIEVAL" });
    const d = computeLabActiveJobResumption({
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

describe("useLabJobSse", () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.spyOn(asyncTask, "sleep").mockResolvedValue(undefined);
    vi.spyOn(asyncTask, "fetchLabJobStatusOnce").mockResolvedValue({
      ...terminalStatus,
      terminal: false,
      status: "RUNNING",
    });
    vi.spyOn(labJobSse, "streamLabJobLive").mockResolvedValue(terminalStatus);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("stays idle when job id or stream path is blank", () => {
    const { result: noJob } = renderHook(() =>
      useLabJobSse({ accepted: { ...accepted, jobId: "  " } }),
    );
    expect(noJob.current.connectionState).toBe("idle");

    const { result: noStream } = renderHook(() =>
      useLabJobSse({ accepted: { ...accepted, streamPath: "" } }),
    );
    expect(noStream.current.connectionState).toBe("idle");
  });

  it("maps unknown terminal snapshot status to failed", async () => {
    vi.mocked(asyncTask.fetchLabJobStatusOnce).mockResolvedValue({
      ...terminalStatus,
      terminal: true,
      status: "WEIRD",
    });
    const { result } = renderHook(() => useLabJobSse({ accepted }));
    await waitFor(() => expect(result.current.connectionState).toBe("failed"));
  });

  it("sets idle when the stream aborts", async () => {
    vi.spyOn(labJobSse, "streamLabJobLive").mockRejectedValue(new DOMException("Aborted", "AbortError"));
    const { result } = renderHook(() => useLabJobSse({ accepted }));
    await waitFor(() => expect(result.current.connectionState).toBe("idle"));
  });

  it("reports idle when disabled", () => {
    const { result } = renderHook(() =>
      useLabJobSse({ accepted, enabled: false }),
    );
    expect(result.current.connectionState).toBe("idle");
  });

  it("completes via live stream when enabled", async () => {
    const onTerminal = vi.fn();
    const { result } = renderHook(() =>
      useLabJobSse({ accepted, onTerminal }),
    );

    await waitFor(() => expect(result.current.connectionState).toBe("completed"));
    expect(result.current.taskStatus?.status).toBe("SUCCEEDED");
    expect(onTerminal).toHaveBeenCalled();
  });

  it("leaves connecting after timeout when hydrate never resolves (SSE-REGRESSION)", async () => {
    vi.useFakeTimers();
    let hydrateCalls = 0;
    vi.mocked(asyncTask.fetchLabJobStatusOnce).mockImplementation(() => {
      hydrateCalls += 1;
      if (hydrateCalls === 1) {
        return new Promise(() => {});
      }
      return Promise.reject(new Error("status poll unavailable"));
    });
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => useLabJobSse({ accepted }));
    expect(result.current.connectionState).toBe("connecting");

    try {
      await act(async () => {
        await vi.advanceTimersByTimeAsync(8_500);
      });
      expect(result.current.connectionState).toBe("configuration_error");
    } finally {
      vi.useRealTimers();
    }
  });

  it("enters reconnecting after timeout when poll still reports RUNNING", async () => {
    vi.useFakeTimers();
    let hydrateCalls = 0;
    vi.mocked(asyncTask.fetchLabJobStatusOnce).mockImplementation(() => {
      hydrateCalls += 1;
      if (hydrateCalls === 1) {
        return new Promise(() => {});
      }
      return Promise.resolve({
        ...terminalStatus,
        terminal: false,
        status: "RUNNING",
      });
    });
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation(
      () => new Promise(() => {}),
    );

    const { result } = renderHook(() => useLabJobSse({ accepted }));
    expect(result.current.connectionState).toBe("connecting");

    try {
      await act(async () => {
        await vi.advanceTimersByTimeAsync(8_500);
      });
      expect(result.current.connectionState).toBe("reconnecting");
    } finally {
      vi.useRealTimers();
    }
  });

  it("short-circuits when snapshot is already terminal", async () => {
    vi.mocked(asyncTask.fetchLabJobStatusOnce).mockResolvedValue(terminalStatus);
    const onTerminal = vi.fn();
    const { result } = renderHook(() =>
      useLabJobSse({ accepted, onTerminal }),
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
    const { result } = renderHook(() => useLabJobSse({ accepted }));

    await waitFor(() => expect(result.current.connectionState).toBe("failed"));
  });

  it("maps cancelled terminal snapshot", async () => {
    vi.mocked(asyncTask.fetchLabJobStatusOnce).mockResolvedValue({
      ...terminalStatus,
      status: "CANCELLED",
    });
    const { result } = renderHook(() => useLabJobSse({ accepted }));

    await waitFor(() => expect(result.current.connectionState).toBe("cancelled"));
  });

  it("maps CANCELED spelling on stream terminal status", async () => {
    vi.spyOn(labJobSse, "streamLabJobLive").mockResolvedValue({
      ...terminalStatus,
      status: "CANCELED",
    });
    const { result } = renderHook(() => useLabJobSse({ accepted }));
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
    const { result, unmount } = renderHook(() => useLabJobSse({ accepted }));
    await waitFor(() => expect(streamCallbacks).toBeDefined());
    act(() => {
      streamCallbacks?.onLive?.();
    });
    await waitFor(() => expect(result.current.connectionState).toBe("live"));
    await new Promise((r) => setTimeout(r, 3_100));
    act(() => {
      streamCallbacks?.onReconnecting?.();
    });
    expect(result.current.connectionState).toBe("reconnecting");
    releaseStream?.();
    unmount();
  });

  it("resume retriggers stream subscription", async () => {
    const { result } = renderHook(() => useLabJobSse({ accepted }));
    await waitFor(() => expect(result.current.connectionState).toBe("completed"));

    result.current.resume();
    await waitFor(() => expect(labJobSse.streamLabJobLive).toHaveBeenCalledTimes(2));
  });

  it("forwards onTick while the stream is running", async () => {
    const onTick = vi.fn();
    renderHook(() => useLabJobSse({ accepted, onTick }));
    await waitFor(() => expect(onTick).toHaveBeenCalled());
  });

  it("reconnects when stream fails but poll still reports RUNNING", async () => {
    const running = {
      ...terminalStatus,
      terminal: false,
      status: "RUNNING",
    };
    vi.mocked(asyncTask.fetchLabJobStatusOnce).mockResolvedValue(running);
    let streamCalls = 0;
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation(() => {
      streamCalls += 1;
      if (streamCalls === 1) {
        return Promise.reject(new Error("stream down"));
      }
      return new Promise(() => {});
    });
    const onStreamError = vi.fn();
    const { result, unmount } = renderHook(() =>
      useLabJobSse({ accepted, onStreamError }),
    );

    await waitFor(() => expect(result.current.connectionState).toBe("reconnecting"));
    expect(onStreamError).not.toHaveBeenCalled();
    unmount();
  });

  it("stays connecting until SSE onLive while stream is opening", async () => {
    let streamCallbacks: LabJobStreamCallbacks | undefined;
    let releaseStream: (() => void) | undefined;
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation((_path, options) => {
      streamCallbacks = options?.callbacks;
      return new Promise((resolve) => {
        releaseStream = () => resolve(terminalStatus);
      });
    });

    const { result, unmount } = renderHook(() => useLabJobSse({ accepted }));
    await waitFor(() => expect(streamCallbacks).toBeDefined());
    expect(result.current.connectionState).toBe("connecting");
    act(() => {
      streamCallbacks?.onLive?.();
    });
    expect(result.current.connectionState).toBe("live");
    releaseStream?.();
    unmount();
  });

  it("stays connecting after hydrate until SSE connects", async () => {
    let streamCallbacks: LabJobStreamCallbacks | undefined;
    let releaseStream: (() => void) | undefined;
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation((_path, options) => {
      streamCallbacks = options?.callbacks;
      return new Promise((resolve) => {
        releaseStream = () => resolve(terminalStatus);
      });
    });
    const { result, unmount } = renderHook(() => useLabJobSse({ accepted }));
    await waitFor(() => expect(streamCallbacks).toBeDefined());
    expect(result.current.connectionState).toBe("connecting");
    act(() => {
      streamCallbacks?.onLive?.();
    });
    await waitFor(() => expect(result.current.connectionState).toBe("live"));
    releaseStream?.();
    unmount();
  });

  it("promotes to live on SNAPSHOT job event with eventId 0", async () => {
    let streamCallbacks: LabJobStreamCallbacks | undefined;
    let releaseStream: (() => void) | undefined;
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation((_path, options) => {
      streamCallbacks = options?.callbacks;
      return new Promise((resolve) => {
        releaseStream = () => resolve(terminalStatus);
      });
    });

    const { result, unmount } = renderHook(() => useLabJobSse({ accepted }));
    await waitFor(() => expect(streamCallbacks).toBeDefined());
    act(() => {
      streamCallbacks?.onJobEvent?.({
        eventId: 0,
        jobId: "job-live-1",
        type: "SNAPSHOT",
        status: "ACCEPTED",
        progress: "Live updates connected.",
        message: "Live updates connected.",
        timestamp: "t",
        payload: { snapshot: true },
      });
    });
    expect(result.current.connectionState).toBe("live");
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

    const { result, unmount } = renderHook(() => useLabJobSse({ accepted }));
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
    const nowSpy = vi.spyOn(Date, "now");
    nowSpy.mockReturnValue(1_000);
    let streamCallbacks: LabJobStreamCallbacks | undefined;
    let releaseStream: (() => void) | undefined;
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation((_path, options) => {
      streamCallbacks = options?.callbacks;
      return new Promise((resolve) => {
        releaseStream = () => resolve(terminalStatus);
      });
    });

    const { result, unmount } = renderHook(() => useLabJobSse({ accepted }));
    await waitFor(() => expect(streamCallbacks).toBeDefined());
    act(() => {
      streamCallbacks?.onLive?.();
    });
    await waitFor(() => expect(result.current.connectionState).toBe("live"));
    nowSpy.mockReturnValue(1_000 + 3_500);
    act(() => {
      streamCallbacks?.onConnecting?.();
    });
    expect(result.current.connectionState).toBe("reconnecting");
    releaseStream?.();
    unmount();
    nowSpy.mockRestore();
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

    const { result, unmount } = renderHook(() => useLabJobSse({ accepted }));
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

    const { result } = renderHook(() => useLabJobSse({ accepted }));
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

    const { result, unmount } = renderHook(() => useLabJobSse({ accepted }));
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
    const decision = computeLabActiveJobResumption({
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
