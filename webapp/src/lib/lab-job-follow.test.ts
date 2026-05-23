import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { followLabJob } from "./lab-job-follow";
import * as asyncTask from "./async-task";
import * as labJobSse from "./lab-job-sse";
import { apiProductPath } from "./api-client";

const accepted = {
  jobId: "job-1",
  status: "ACCEPTED",
  pollPath: apiProductPath("/lab/jobs/job-1"),
  streamPath: apiProductPath("/lab/jobs/job-1/events"),
};

describe("followLabJob", () => {
  beforeEach(() => {
    vi.spyOn(asyncTask, "pollLabJob").mockResolvedValue({
      terminal: true,
      status: "SUCCEEDED",
    } as never);
    vi.spyOn(labJobSse, "streamLabJob").mockResolvedValue({
      terminal: true,
      status: "SUCCEEDED",
    } as never);
    vi.spyOn(labJobSse, "streamLabJobLive").mockResolvedValue({
      terminal: true,
      status: "SUCCEEDED",
    } as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("delegates to pollLabJob when mode is poll", async () => {
    const onTick = vi.fn();
    await followLabJob(accepted, onTick, { mode: "poll" });

    expect(asyncTask.pollLabJob).toHaveBeenCalledWith(
      "job-1",
      onTick,
      expect.objectContaining({
        signal: undefined,
        intervalMs: undefined,
        throwOnFailed: undefined,
        maxWaitMs: undefined,
      }),
    );
    expect(labJobSse.streamLabJob).not.toHaveBeenCalled();
  });

  it("delegates to streamLabJobLive by default (sse)", async () => {
    const onTick = vi.fn();
    await followLabJob(accepted, onTick);

    expect(labJobSse.streamLabJobLive).toHaveBeenCalledWith(
      accepted.streamPath,
      expect.objectContaining({
        callbacks: expect.objectContaining({ onTaskTick: onTick }),
      }),
    );
    expect(labJobSse.streamLabJob).not.toHaveBeenCalled();
    expect(asyncTask.pollLabJob).not.toHaveBeenCalled();
  });

  it("delegates to streamLabJobLive when mode is sse", async () => {
    const onTick = vi.fn();
    const ac = new AbortController();
    await followLabJob(accepted, onTick, {
      mode: "sse",
      signal: ac.signal,
    });

    expect(labJobSse.streamLabJobLive).toHaveBeenCalledWith(
      accepted.streamPath,
      expect.objectContaining({ signal: ac.signal, callbacks: expect.objectContaining({ onTaskTick: onTick }) }),
    );
    expect(labJobSse.streamLabJob).not.toHaveBeenCalled();
    expect(asyncTask.pollLabJob).not.toHaveBeenCalled();
  });

  it("forwards poll options", async () => {
    const ac = new AbortController();
    await followLabJob(accepted, () => {}, {
      mode: "poll",
      signal: ac.signal,
      intervalMs: 100,
      throwOnFailed: false,
    });

    expect(asyncTask.pollLabJob).toHaveBeenCalledWith("job-1", expect.any(Function), {
      signal: ac.signal,
      intervalMs: 100,
      throwOnFailed: false,
      maxWaitMs: undefined,
    });
  });

  it("forwards maxWaitMs for classifier poll watchdog (Phase 6C)", async () => {
    await followLabJob(accepted, () => {}, { mode: "poll", maxWaitMs: 120_000 });

    expect(asyncTask.pollLabJob).toHaveBeenCalledWith(
      "job-1",
      expect.any(Function),
      expect.objectContaining({ maxWaitMs: 120_000 }),
    );
  });

  it("uses streamLabJobLive when liveReconnect is explicitly enabled", async () => {
    const onTick = vi.fn();
    const callbacks = { onReconnecting: vi.fn() };
    await followLabJob(accepted, onTick, {
      mode: "sse",
      liveReconnect: true,
      sinceEventId: 3,
      callbacks,
    });

    expect(labJobSse.streamLabJobLive).toHaveBeenCalledWith(
      accepted.streamPath,
      expect.objectContaining({
        sinceEventId: 3,
        callbacks: expect.objectContaining({ onTaskTick: onTick, onReconnecting: callbacks.onReconnecting }),
      }),
    );
    expect(labJobSse.streamLabJob).not.toHaveBeenCalled();
  });

  it("forwards sinceEventId to streamLabJobLive in sse mode", async () => {
    await followLabJob(accepted, () => {}, { mode: "sse", sinceEventId: 9 });
    expect(labJobSse.streamLabJobLive).toHaveBeenCalledWith(
      accepted.streamPath,
      expect.objectContaining({ sinceEventId: 9 }),
    );
  });

  it("uses single-shot streamLabJob when liveReconnect is false", async () => {
    const onTick = vi.fn();
    await followLabJob(accepted, onTick, { mode: "sse", liveReconnect: false });
    expect(labJobSse.streamLabJob).toHaveBeenCalledWith(accepted.streamPath, onTick, expect.any(Object));
    expect(labJobSse.streamLabJobLive).not.toHaveBeenCalled();
  });
});
