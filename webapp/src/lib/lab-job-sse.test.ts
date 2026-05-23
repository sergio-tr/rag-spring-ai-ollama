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

  it("adds leading slash when streamPath has no slash prefix", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: RequestInfo) => {
        expect(String(url)).toBe("http://localhost:9000/lab/stream");
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
        text: () => Promise.resolve("upstream"),
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
        text: () => Promise.reject(new Error("read failed")),
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
});

describe("eventToAsyncTaskStatus", () => {
  it("returns null for heartbeat and invalid event ids", () => {
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

  it("falls back to poll after SSE retries are exhausted", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network")));
    const terminal = {
      id: "job-1",
      taskType: "LAB",
      status: "SUCCEEDED",
      terminal: true,
      progressText: null,
      result: null,
      errorMessage: null,
      createdAt: "",
      updatedAt: "",
      startedAt: null,
      completedAt: null,
      failureCode: null,
    };
    const poll = vi.spyOn(asyncTask, "pollLabJob").mockImplementation(async (_id, onTick) => {
      onTick(terminal);
      return terminal;
    });
    const onFinishedAway = vi.fn();

    const out = await streamLabJobLive(apiProductPath("/lab/jobs/job-1/events"), {
      callbacks: { onFinishedAway },
    });

    expect(out.status).toBe("SUCCEEDED");
    expect(poll).toHaveBeenCalled();
    expect(onFinishedAway).toHaveBeenCalled();
  });

  it("throws when stream path has no job id for poll fallback", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network")));

    await expect(streamLabJobLive("/lab/unknown", {})).rejects.toThrow("Live stream unavailable");
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

  it("invokes onReconnecting after first failed attempt", async () => {
    let calls = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() => {
        calls += 1;
        if (calls === 1) {
          return Promise.reject(new Error("fail"));
        }
        return Promise.resolve(
          mockFetchWithStream([
            encodeLines(['event: task', 'data: {"terminal":true,"status":"SUCCEEDED"}', ""]),
          ]),
        );
      }),
    );
    const onReconnecting = vi.fn();

    await streamLabJobLive(apiProductPath("/lab/jobs/job-1/events"), {
      callbacks: { onReconnecting },
    });

    expect(onReconnecting).toHaveBeenCalled();
  });
});
