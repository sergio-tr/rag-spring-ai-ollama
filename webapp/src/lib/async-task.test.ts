import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { pollLabJob, sleep } from "./async-task";
import * as apiClient from "./api-client";

describe("sleep", () => {
  it("resolves after delay", async () => {
    const t0 = Date.now();
    await sleep(20);
    expect(Date.now() - t0).toBeGreaterThanOrEqual(15);
  });
});

describe("pollLabJob", () => {
  beforeEach(() => {
    vi.spyOn(apiClient, "apiFetch").mockRejectedValue(new Error("unmocked"));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns when terminal succeeded", async () => {
    const finalStatus = {
      terminal: true,
      status: "SUCCEEDED",
      errorMessage: null as string | null,
    };
    vi.mocked(apiClient.apiFetch).mockResolvedValueOnce(finalStatus as never);

    const out = await pollLabJob("job-1", () => {});
    expect(out).toBe(finalStatus);
    expect(apiClient.apiFetch).toHaveBeenCalledWith(apiClient.apiProductPath("/lab/jobs/job-1"));
  });

  it("throws on FAILED", async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue({
      terminal: true,
      status: "FAILED",
      errorMessage: "nope",
    } as never);

    await expect(pollLabJob("j", () => {})).rejects.toThrow("nope");
  });

  it("throws AbortError when aborted before fetch", async () => {
    const ac = new AbortController();
    ac.abort();
    await expect(pollLabJob("j", () => {}, { signal: ac.signal })).rejects.toThrow();
  });

  it("polls until terminal", async () => {
    vi.mocked(apiClient.apiFetch)
      .mockResolvedValueOnce({ terminal: false, status: "RUNNING" } as never)
      .mockResolvedValueOnce({ terminal: true, status: "SUCCEEDED" } as never);

    const ticks: string[] = [];
    await pollLabJob("j", (s) => ticks.push(s.status), { intervalMs: 1 });

    expect(apiClient.apiFetch).toHaveBeenCalledTimes(2);
    expect(ticks).toEqual(["RUNNING", "SUCCEEDED"]);
  });
});
