import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { useLabJobSse } from "./use-lab-job-sse";
import * as asyncTask from "@/lib/async-task";
import * as labJobSse from "@/lib/lab-job-sse";
import type { LabJobAcceptedDto } from "@/types/api";

const accepted: LabJobAcceptedDto = {
  jobId: "job-disconnect-1",
  status: "QUEUED",
  pollPath: "/lab/jobs/job-disconnect-1",
  streamPath: "/lab/jobs/job-disconnect-1/events",
};

const runningStatus = {
  id: "job-disconnect-1",
  taskType: "EVAL_RAG",
  status: "RUNNING",
  progressText: "1/3",
  result: null,
  errorMessage: null,
  terminal: false,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:01Z",
  startedAt: "2026-01-01T00:00:01Z",
  completedAt: null,
  failureCode: null,
};

describe("LabJobSseDisconnectHandling", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(asyncTask, "fetchLabJobStatusOnce").mockResolvedValue(runningStatus);
  });

  it("stop closes stream without surfacing stream error", async () => {
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation(
      (_path, options) =>
        new Promise((_, reject) => {
          options?.signal?.addEventListener("abort", () => {
            reject(new DOMException("Aborted", "AbortError"));
          });
        }),
    );
    const onStreamError = vi.fn();
    const { result } = renderHook(() => useLabJobSse({ accepted, onStreamError }));

    await waitFor(() => expect(result.current.connectionState).toBe("connecting"));
    result.current.stop();
    await waitFor(() => expect(result.current.connectionState).toBe("idle"));
    expect(onStreamError).not.toHaveBeenCalled();
  });

  it("navigate-away abort does not call onStreamError", async () => {
    vi.spyOn(labJobSse, "streamLabJobLive").mockImplementation(
      (_path, options) =>
        new Promise((_, reject) => {
          options?.signal?.addEventListener("abort", () => {
            reject(new DOMException("Aborted", "AbortError"));
          });
        }),
    );
    const onStreamError = vi.fn();
    const { unmount } = renderHook(() => useLabJobSse({ accepted, onStreamError }));

    await waitFor(() => expect(labJobSse.streamLabJobLive).toHaveBeenCalled());
    unmount();
    await waitFor(() => expect(onStreamError).not.toHaveBeenCalled());
  });
});
