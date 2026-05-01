import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { streamLabJob } from "./lab-job-sse";
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
});
