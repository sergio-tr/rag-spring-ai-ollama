import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { eventToAsyncTaskStatus, streamLabJob, streamLabJobLive } from "./lab-job-sse";
import * as asyncTask from "./async-task";
import * as accessToken from "./access-token";
import * as apiClient from "./api-client";
import { apiProductPath } from "./api-client";

function encodeLines(lines: string[]): Uint8Array {
  return new TextEncoder().encode(lines.join("\n") + "\n");
}

function mockFetchWithStream(chunks: Uint8Array[], ok = true, status = 200) {
  let i = 0;
  return {
    ok,
    status,
    statusText: ok ? "OK" : "Error",
    headers: new Headers({ "content-type": "text/event-stream" }),
    clone: () => ({
      text: () => Promise.resolve(""),
    }),
    text: () => Promise.resolve("err-body"),
    body: new ReadableStream<Uint8Array>({
      pull(controller) {
        if (i >= chunks.length) {
          controller.close();
          return;
        }
        controller.enqueue(chunks[i]!);
        i += 1;
      },
    }),
  } as unknown as Response;
}

describe("streamLabJob", () => {
  beforeEach(() => {
    vi.spyOn(apiClient, "getApiBaseUrl").mockReturnValue("http://localhost:9000");
    const toBackend = (path: string) =>
      `http://localhost:9000${path.startsWith("/") ? path : `/${path}`}`;
    vi.spyOn(apiClient, "resolveBrowserProductApiUrl").mockImplementation(toBackend);
    vi.spyOn(apiClient, "resolveLabJobApiUrl").mockImplementation(toBackend);
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue(null);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("uses absolute streamPath when it is already http(s)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: RequestInfo) => {
        expect(String(url)).toBe("https://api.example.com/lab/jobs/x/events");
        return Promise.resolve(
          mockFetchWithStream([
            encodeLines(['event: task', 'data: {"terminal":true,"status":"SUCCEEDED"}', ""]),
          ]),
        );
      }),
    );

    const out = await streamLabJob("https://api.example.com/lab/jobs/x/events", () => {});
    expect(out.terminal).toBe(true);
    expect(out.status).toBe("SUCCEEDED");
  });

  it("prefixes relative path with API base", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: RequestInfo) => {
        expect(String(url)).toBe(`http://localhost:9000${apiProductPath("/lab/jobs/e/events")}`);
        return Promise.resolve(
          mockFetchWithStream([
            encodeLines(['event: task', 'data: {"terminal":true,"status":"SUCCEEDED"}', ""]),
          ]),
        );
      }),
    );

    await streamLabJob(apiProductPath("/lab/jobs/e/events"), () => {});
  });

  it("normalizes GET /lab/jobs/active style paths with product prefix", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: RequestInfo) => {
        expect(String(url)).toBe(`http://localhost:9000${apiProductPath("/lab/jobs/job-1/events")}`);
        return Promise.resolve(
          mockFetchWithStream([
            encodeLines([
              'event: job-event',
              'data: {"eventId":1,"jobId":"job-1","type":"COMPLETED","status":"SUCCEEDED","terminal":true,"timestamp":"t","payload":{"terminal":true}}',
              "",
            ]),
          ]),
        );
      }),
    );

    await streamLabJob("/lab/jobs/job-1/events", () => {});
  });

  it("adds leading slash when streamPath has no slash prefix", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: RequestInfo) => {
        expect(String(url)).toBe(`http://localhost:9000${apiProductPath("/lab/stream")}`);
        return Promise.resolve(
          mockFetchWithStream([
            encodeLines(['event: task', 'data: {"terminal":true,"status":"SUCCEEDED"}', ""]),
          ]),
        );
      }),
    );

    await streamLabJob("lab/stream", () => {});
  });

  it("adds Bearer when access token exists", async () => {
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue("tok");
    const fetchMock = vi.fn().mockResolvedValue(
      mockFetchWithStream([
        encodeLines(['event: task', 'data: {"terminal":true,"status":"SUCCEEDED"}', ""]),
      ]),
    );
    vi.stubGlobal("fetch", fetchMock);

    await streamLabJob("/events", () => {});

    const init = fetchMock.mock.calls[0]![1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe("Bearer tok");
    expect(h.Accept).toBe("text/event-stream");
  });

  it("throws when response is not ok", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 502,
        statusText: "Bad Gateway",
        headers: new Headers({ "content-type": "application/json" }),
        text: () => Promise.resolve("upstream"),
        clone: () => ({ text: () => Promise.resolve("upstream") }),
      } as Response),
    );

    await expect(streamLabJob("/e", () => {})).rejects.toThrow("upstream");
  });

  it("uses statusText when error body is empty and text() fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 503,
        statusText: "Service Unavailable",
        headers: new Headers({ "content-type": "application/json" }),
        text: () => Promise.reject(new Error("read failed")),
        clone: () => ({ text: () => Promise.reject(new Error("read failed")) }),
      } as Response),
    );

    await expect(streamLabJob("/e", () => {})).rejects.toThrow("Service Unavailable");
  });

  it("throws when response body is missing", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "text/event-stream" }),
        clone: () => ({ text: () => Promise.resolve("") }),
        body: null,
      } as Response),
    );

    await expect(streamLabJob("/e", () => {})).rejects.toThrow("No response body");
  });

  it("throws Job failed when FAILED carries HTML-like errorMessage", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([
          encodeLines([
            "event: task",
            'data: {"terminal":true,"status":"FAILED","errorMessage":"<html><body>502</body></html>"}',
            "",
          ]),
        ]),
      ),
    );

    await expect(streamLabJob("/e", () => {})).rejects.toThrow("Job failed");
  });

  it("throws on terminal FAILED", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([
          encodeLines([
            'event: task',
            'data: {"terminal":true,"status":"FAILED","errorMessage":"boom"}',
            "",
          ]),
        ]),
      ),
    );

    await expect(streamLabJob("/e", () => {})).rejects.toThrow("boom");
  });

  it("throws Job failed when FAILED has no errorMessage", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([
          encodeLines(['event: task', 'data: {"terminal":true,"status":"FAILED"}', ""]),
        ]),
      ),
    );

    await expect(streamLabJob("/e", () => {})).rejects.toThrow("Job failed");
  });

  it("ignores non-JSON data lines with SyntaxError", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([
          encodeLines(["event: task", "data: not-json", "", 'data: {"terminal":true,"status":"SUCCEEDED"}', ""]),
        ]),
      ),
    );

    const out = await streamLabJob("/e", () => {});
    expect(out.status).toBe("SUCCEEDED");
  });

  it("skips non-task JSON lines that do not look like objects", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([
          encodeLines([
            "event: ping",
            "data: plain",
            "",
            'event: task',
            'data: {"terminal":true,"status":"SUCCEEDED"}',
            "",
          ]),
        ]),
      ),
    );

    const out = await streamLabJob("/e", () => {});
    expect(out.terminal).toBe(true);
  });

  it("treats empty event name as task for JSON data", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([encodeLines(['data: {"terminal":true,"status":"SUCCEEDED"}', ""])]),
      ),
    );

    const out = await streamLabJob("/e", () => {});
    expect(out.status).toBe("SUCCEEDED");
  });

  it("calls onTick for non-terminal updates", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([
          encodeLines([
            'event: task',
            'data: {"terminal":false,"status":"RUNNING"}',
            "",
            'data: {"terminal":true,"status":"SUCCEEDED"}',
            "",
          ]),
        ]),
      ),
    );

    const ticks: string[] = [];
    await streamLabJob("/e", (s) => ticks.push(s.status));

    expect(ticks).toEqual(["RUNNING", "SUCCEEDED"]);
  });

  it("throws when stream ends before terminal", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(mockFetchWithStream([encodeLines(["event: ping", "data: hello", ""])])),
    );

    await expect(streamLabJob("/e", () => {})).rejects.toThrow("SSE stream ended before job completed");
  });

  it("skips [DONE] and empty data", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([
          encodeLines(["data: [DONE]", "", 'data: {"terminal":true,"status":"SUCCEEDED"}', ""]),
        ]),
      ),
    );

    const out = await streamLabJob("/e", () => {});
    expect(out.status).toBe("SUCCEEDED");
  });

  it("tracks SSE id lines and parses job-event FAILED terminal", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([
          encodeLines([
            "id: 7",
            "event: job-event",
            'data: {"eventId":7,"jobId":"j1","type":"FAILED","status":"FAILED","progress":null,"message":null,"timestamp":"t","payload":{"errorMessage":"bad run","terminal":true}}',
            "",
          ]),
        ]),
      ),
    );

    await expect(streamLabJob("/e", () => {})).rejects.toThrow("bad run");
  });

  it("appends since query param when sinceEventId is set", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: RequestInfo) => {
        expect(String(url)).toContain("since=42");
        return Promise.resolve(
          mockFetchWithStream([
            encodeLines(['event: task', 'data: {"terminal":true,"status":"SUCCEEDED"}', ""]),
          ]),
        );
      }),
    );

    await streamLabJob("/lab/jobs/j1/events?foo=1", () => {}, { sinceEventId: 42 });
  });

  it("handles job-event terminal COMPLETED", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([
          encodeLines([
            "event: job-event",
            'data: {"eventId":1,"jobId":"j1","type":"COMPLETED","status":"SUCCEEDED","progress":null,"timestamp":"t","payload":{}}',
            "",
          ]),
        ]),
      ),
    );

    const out = await streamLabJob("/e", () => {}, { sinceEventId: 0 });
    expect(out.status).toBe("SUCCEEDED");
  });

  it("invokes onLive on initial SNAPSHOT with eventId 0 (SSE-REGRESSION)", async () => {
    const onLive = vi.fn();
    const onTick = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([
          encodeLines([
            "id:0",
            "event: job-event",
            'data: {"eventId":0,"jobId":"job-new","type":"SNAPSHOT","status":"ACCEPTED","progress":null,"message":"Live updates connected.","timestamp":"2026-01-01T00:00:00Z","payload":{"snapshot":true}}',
            "",
          ]),
        ]),
      ),
    );

    const ac = new AbortController();
    const pending = streamLabJobLive(apiProductPath("/lab/jobs/job-new/events"), {
      signal: ac.signal,
      callbacks: { onLive, onTaskTick: onTick },
    });
    await vi.waitFor(() => expect(onTick).toHaveBeenCalled());
    ac.abort();
    await expect(pending).rejects.toMatchObject({ name: "AbortError" });

    expect(onLive).toHaveBeenCalled();
    expect(onTick).toHaveBeenCalledWith(
      expect.objectContaining({ status: "ACCEPTED", terminal: false }),
    );
  });
});

describe("eventToAsyncTaskStatus", () => {
  it("returns null for heartbeat", () => {
    expect(
      eventToAsyncTaskStatus(
        {
          eventId: 0,
          jobId: "j",
          type: "HEARTBEAT",
          status: null,
          progress: null,
          message: null,
          timestamp: "t",
          payload: null,
        },
        null,
      ),
    ).toBeNull();
  });

  it("maps SNAPSHOT with eventId 0 to task status", () => {
    const mapped = eventToAsyncTaskStatus(
      {
        eventId: 0,
        jobId: "job-new",
        type: "SNAPSHOT",
        status: "ACCEPTED",
        progress: "Live updates connected.",
        message: "Live updates connected.",
        timestamp: "2026-01-01T00:00:00Z",
        payload: { snapshot: true },
      },
      null,
    );
    expect(mapped?.status).toBe("ACCEPTED");
    expect(mapped?.terminal).toBe(false);
    expect(mapped?.id).toBe("job-new");
  });

  it("returns null for non-snapshot events with invalid event ids", () => {
    expect(
      eventToAsyncTaskStatus(
        {
          eventId: 0,
          jobId: "j",
          type: "ACCEPTED",
          status: "QUEUED",
          progress: null,
          message: null,
          timestamp: "t",
          payload: null,
        },
        null,
      ),
    ).toBeNull();
  });

  it("maps FAILED and CANCELLED events", () => {
    const failed = eventToAsyncTaskStatus(
      {
        eventId: 2,
        jobId: "j",
        type: "FAILED",
        status: null,
        progress: "err",
        message: null,
        timestamp: "t2",
        payload: { errorMessage: "nope", terminal: true },
      },
      null,
    );
    expect(failed?.status).toBe("FAILED");
    expect(failed?.terminal).toBe(true);

    const cancelled = eventToAsyncTaskStatus(
      {
        eventId: 3,
        jobId: "j",
        type: "CANCELLED",
        status: null,
        progress: null,
        message: null,
        timestamp: "t3",
        payload: {},
      },
      null,
    );
    expect(cancelled?.status).toBe("CANCELLED");
  });

  it("maps COMPLETED type to SUCCEEDED when status is omitted", () => {
    const mapped = eventToAsyncTaskStatus(
      {
        eventId: 6,
        jobId: "j",
        type: "COMPLETED",
        status: null,
        progress: null,
        message: null,
        timestamp: "t6",
        payload: {},
      },
      null,
    );
    expect(mapped?.status).toBe("SUCCEEDED");
    expect(mapped?.terminal).toBe(true);
  });

  it("uses explicit event.status when provided", () => {
    const mapped = eventToAsyncTaskStatus(
      {
        eventId: 5,
        jobId: "j",
        type: "PROGRESS",
        status: "QUEUED",
        progress: null,
        message: null,
        timestamp: "t5",
        payload: {},
      },
      null,
    );
    expect(mapped?.status).toBe("QUEUED");
  });

  it("preserves previous task fields for progress events", () => {
    const prev = {
      id: "j",
      taskType: "LAB",
      status: "RUNNING",
      progressText: "old",
      result: { x: 1 },
      errorMessage: null,
      terminal: false,
      createdAt: "t0",
      updatedAt: "t0",
      startedAt: "t0",
      completedAt: null,
      failureCode: null,
    };
    const next = eventToAsyncTaskStatus(
      {
        eventId: 4,
        jobId: "j",
        type: "PROGRESS",
        status: "RUNNING",
        progress: "new",
        message: null,
        timestamp: "t4",
        payload: {},
      },
      prev,
    );
    expect(next?.progressText).toBe("new");
    expect(next?.result).toEqual({ x: 1 });
  });
});

describe("streamLabJobLive", () => {
  beforeEach(() => {
    vi.spyOn(apiClient, "getApiBaseUrl").mockReturnValue("http://localhost:9000");
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue(null);
    vi.spyOn(asyncTask, "sleep").mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("retries SSE without polling after transient failures", async () => {
    let calls = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() => {
        calls += 1;
        if (calls < 3) {
          return Promise.reject(new Error("network"));
        }
        return Promise.resolve(
          mockFetchWithStream([
            encodeLines(['event: task', 'data: {"terminal":true,"status":"SUCCEEDED"}', ""]),
          ]),
        );
      }),
    );
    const poll = vi.spyOn(asyncTask, "pollLabJob");
    const onReconnecting = vi.fn();

    const out = await streamLabJobLive(apiProductPath("/lab/jobs/job-1/events"), {
      callbacks: { onReconnecting },
    });

    expect(out.status).toBe("SUCCEEDED");
    expect(poll).not.toHaveBeenCalled();
    expect(onReconnecting).not.toHaveBeenCalled();
    expect(calls).toBeGreaterThanOrEqual(3);
  });

  it("aborts retry loop when signal is aborted", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network")));
    const ac = new AbortController();
    ac.abort();
    const poll = vi.spyOn(asyncTask, "pollLabJob");

    await expect(streamLabJobLive("/lab/unknown", { signal: ac.signal })).rejects.toMatchObject({
      name: "AbortError",
    });
    expect(poll).not.toHaveBeenCalled();
  });

  it("rethrows AbortError without polling", async () => {
    const ac = new AbortController();
    ac.abort();
    const poll = vi.spyOn(asyncTask, "pollLabJob");

    await expect(
      streamLabJobLive(apiProductPath("/lab/jobs/job-1/events"), { signal: ac.signal }),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(poll).not.toHaveBeenCalled();
  });

  it("invokes onLive for heartbeat events", async () => {
    const onLive = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockFetchWithStream([
          encodeLines([
            "event: heartbeat",
            'data: {"eventId":0,"jobId":"j1","type":"HEARTBEAT","status":null,"progress":null,"message":null,"timestamp":"t","payload":null}',
            "",
            'event: task',
            'data: {"terminal":true,"status":"SUCCEEDED"}',
            "",
          ]),
        ]),
      ),
    );

    await streamLabJobLive(apiProductPath("/lab/jobs/job-1/events"), {
      callbacks: { onLive, onTaskTick: () => {} },
    });
    expect(onLive).toHaveBeenCalled();
  });

  it("throws configuration error on HTML 404 response (Next dev mis-route)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        headers: new Headers({ "content-type": "text/html; charset=utf-8" }),
        clone: () => ({
          text: () => Promise.resolve("<!DOCTYPE html><html><body>404</body></html>"),
        }),
      }),
    );

    await expect(streamLabJob("/lab/jobs/j1/events", () => {})).rejects.toMatchObject({
      name: "LabSseConfigurationError",
      message: /reached the web application instead of the backend API/,
    });
  });

  it("does not retry on HTML 404 configuration error", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      headers: new Headers({ "content-type": "text/html; charset=utf-8" }),
      clone: () => ({
        text: () => Promise.resolve("<!DOCTYPE html><html><body>404</body></html>"),
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      streamLabJobLive(apiProductPath("/lab/jobs/job-1/events"), { signal: AbortSignal.timeout(5_000) }),
    ).rejects.toMatchObject({ name: "LabSseConfigurationError" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("invokes onReconnecting only after a prior successful connection", async () => {
    vi.spyOn(asyncTask, "sleep").mockResolvedValue(undefined);
    let calls = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() => {
        calls += 1;
        if (calls === 1) {
          return Promise.resolve(
            mockFetchWithStream([
              encodeLines([
                "event: job-event",
                'data: {"eventId":0,"jobId":"j1","type":"SNAPSHOT","status":"RUNNING","timestamp":"t","payload":{}}',
                "",
                'event: task',
                'data: {"terminal":true,"status":"SUCCEEDED"}',
                "",
              ]),
            ]),
          );
        }
        if (calls === 2) {
          return Promise.reject(new Error("disconnect"));
        }
        return Promise.resolve(
          mockFetchWithStream([
            encodeLines(['event: task', 'data: {"terminal":true,"status":"SUCCEEDED"}', ""]),
          ]),
        );
      }),
    );
    const onReconnecting = vi.fn();
    const onLive = vi.fn();

    const out = await streamLabJobLive(apiProductPath("/lab/jobs/job-1/events"), {
      callbacks: { onReconnecting, onLive },
    });
    expect(out.status).toBe("SUCCEEDED");
    expect(onLive).toHaveBeenCalled();
    expect(onReconnecting).not.toHaveBeenCalled();
  });
});
