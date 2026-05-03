import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useJobPolling } from "./use-job-polling";

describe("useJobPolling", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("polls fetcher and stops when stopWhen returns true", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce({ done: false })
      .mockResolvedValueOnce({ done: true });
    const onUpdate = vi.fn();
    renderHook(() =>
      useJobPolling({
        intervalMs: 1000,
        fetcher,
        onUpdate,
        stopWhen: (d: { done: boolean }) => d.done,
      }),
    );
    await vi.advanceTimersByTimeAsync(1000);
    expect(fetcher).toHaveBeenCalled();
    expect(onUpdate).toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1000);
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("does not schedule when disabled", () => {
    const fetcher = vi.fn();
    renderHook(() =>
      useJobPolling({
        enabled: false,
        fetcher,
        intervalMs: 500,
      }),
    );
    void vi.advanceTimersByTimeAsync(2000);
    expect(fetcher).not.toHaveBeenCalled();
  });
});
