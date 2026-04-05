import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { pollAccountJob, pollLabJob, sleep } from "./async-task";
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

  it("does not throw on FAILED when throwOnFailed is false", async () => {
    const failed = {
      terminal: true,
      status: "FAILED",
      errorMessage: "soft fail",
    };
    vi.mocked(apiClient.apiFetch).mockResolvedValue(failed as never);

    const out = await pollLabJob("j", () => {}, { throwOnFailed: false });
    expect(out.status).toBe("FAILED");
    expect(out.errorMessage).toBe("soft fail");
  });
});

describe("pollAccountJob", () => {
  beforeEach(() => {
    vi.spyOn(apiClient, "apiFetch").mockRejectedValue(new Error("unmocked"));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("polls me account jobs path until terminal", async () => {
    vi.mocked(apiClient.apiFetch)
      .mockResolvedValueOnce({ terminal: false, status: "RUNNING" } as never)
      .mockResolvedValueOnce({ terminal: true, status: "SUCCEEDED" } as never);

    await pollAccountJob("acc-1", () => {}, { intervalMs: 1 });

    expect(apiClient.apiFetch).toHaveBeenLastCalledWith(
      apiClient.apiProductPath("/me/account/jobs/acc-1"),
    );
  });

  it("throws on FAILED by default", async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue({
      terminal: true,
      status: "FAILED",
      errorMessage: "export failed",
    } as never);

    await expect(pollAccountJob("x", () => {})).rejects.toThrow("export failed");
  });

  it("respects throwOnFailed false", async () => {
    const st = { terminal: true, status: "FAILED", errorMessage: null };
    vi.mocked(apiClient.apiFetch).mockResolvedValue(st as never);

    const out = await pollAccountJob("y", () => {}, { throwOnFailed: false });
    expect(out.status).toBe("FAILED");
  });

  it("throws AbortError when signal aborted", async () => {
    const ac = new AbortController();
    ac.abort();
    await expect(pollAccountJob("z", () => {}, { signal: ac.signal })).rejects.toThrow();
  });
});
