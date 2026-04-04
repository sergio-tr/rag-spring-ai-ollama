import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { postSseJson } from "./sse-post";
import * as accessToken from "./access-token";

function streamResponse(lines: string[]): Response {
  const enc = new TextEncoder();
  const payload = lines.join("\n") + "\n";
  let sent = false;
  return {
    ok: true,
    status: 200,
    body: new ReadableStream({
      pull(controller) {
        if (sent) {
          controller.close();
          return;
        }
        sent = true;
        controller.enqueue(enc.encode(payload));
      },
    }),
  } as unknown as Response;
}

describe("postSseJson", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(new Error("unmocked fetch"))),
    );
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue(null);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("invokes onError when response not ok", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 502,
      statusText: "Bad",
      text: () => Promise.resolve("upstream"),
    } as Response);

    const onError = vi.fn();
    await postSseJson("/api/x", {}, undefined, { onError });

    expect(onError).toHaveBeenCalledWith("upstream", "502");
  });

  it("invokes onError when body missing", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      status: 200,
      body: null,
    } as Response);

    const onError = vi.fn();
    await postSseJson("/api/x", {}, undefined, { onError });

    expect(onError).toHaveBeenCalledWith("No response body");
  });

  it("parses delta and done events", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce(
      streamResponse([
        "event: delta",
        'data: {"text":"hi"}',
        "",
        "event: done",
        'data: {"sources":[]}',
        "",
      ]),
    );

    const onDelta = vi.fn();
    const onDone = vi.fn();
    await postSseJson("/api/x", { q: 1 }, undefined, { onDelta, onDone });

    expect(onDelta).toHaveBeenCalledWith("hi");
    expect(onDone).toHaveBeenCalledWith({ sources: [] });
  });

  it("parses error event JSON", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce(
      streamResponse(["event: error", 'data: {"code":"E1","message":"bad"}', ""]),
    );

    const onError = vi.fn();
    await postSseJson("/api/x", {}, undefined, { onError });

    expect(onError).toHaveBeenCalledWith("bad", "E1");
  });

  it("ignores malformed delta JSON", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce(
      streamResponse(["event: delta", "data: not-json", ""]),
    );

    const onDelta = vi.fn();
    await postSseJson("/api/x", {}, undefined, { onDelta });

    expect(onDelta).not.toHaveBeenCalled();
  });

  it("handles [DONE] data line", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce(streamResponse(["data: [DONE]", ""]));

    const onDone = vi.fn();
    await postSseJson("/api/x", {}, undefined, { onDone });

    expect(onDone).not.toHaveBeenCalled();
  });

  it("invokes onError for invalid done payload", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce(
      streamResponse(["event: done", "data: not-json", ""]),
    );

    const onError = vi.fn();
    await postSseJson("/api/x", {}, undefined, { onError });

    expect(onError).toHaveBeenCalledWith("Invalid done payload");
  });

  it("invokes onError with raw line when error event JSON is invalid", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce(streamResponse(["event: error", "data: {", ""]));

    const onError = vi.fn();
    await postSseJson("/api/x", {}, undefined, { onError });

    expect(onError).toHaveBeenCalledWith("{");
  });

  it("adds Authorization when token present", async () => {
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue("tok");
    vi.mocked(globalThis.fetch).mockResolvedValueOnce(streamResponse([]));

    await postSseJson("/api/x", {}, undefined, {});

    expect(globalThis.fetch).toHaveBeenCalled();
    const init = vi.mocked(globalThis.fetch).mock.calls[0]![1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe("Bearer tok");
  });

  it("calls onAbort when signal is already aborted", async () => {
    const ac = new AbortController();
    ac.abort();
    const onAbort = vi.fn();
    await postSseJson("/api/x", {}, ac.signal, { onAbort });

    expect(onAbort).toHaveBeenCalledTimes(1);
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it("calls onAbort when fetch rejects with AbortError", async () => {
    const ac = new AbortController();
    vi.mocked(globalThis.fetch).mockImplementation((_url, init) => {
      const sig = init?.signal as AbortSignal | undefined;
      return new Promise<Response>((_, reject) => {
        const onAbort = () => {
          reject(new DOMException("Aborted", "AbortError"));
        };
        if (sig?.aborted) {
          onAbort();
          return;
        }
        sig?.addEventListener("abort", onAbort, { once: true });
      });
    });

    const onAbort = vi.fn();
    const p = postSseJson("/api/x", {}, ac.signal, { onAbort });
    ac.abort();
    await p;

    expect(onAbort).toHaveBeenCalledTimes(1);
  });

  it("invokes onError on generic network failure", async () => {
    vi.mocked(globalThis.fetch).mockRejectedValueOnce(new Error("offline"));
    const onError = vi.fn();
    await postSseJson("/api/x", {}, undefined, { onError });
    expect(onError).toHaveBeenCalledWith("offline", "NETWORK");
  });

  it("invokes onError STREAM when read throws a non-abort Error", async () => {
    const reader = {
      read: () => Promise.reject(new Error("broken stream")),
      cancel: () => Promise.resolve(),
    };
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      status: 200,
      body: { getReader: () => reader },
    } as unknown as Response);

    const onError = vi.fn();
    await postSseJson("/api/x", {}, undefined, { onError });
    expect(onError).toHaveBeenCalledWith("broken stream", "STREAM");
  });

  it("treats Error named AbortError as abort when fetch rejects", async () => {
    const err = new Error("aborted");
    err.name = "AbortError";
    vi.mocked(globalThis.fetch).mockRejectedValueOnce(err);
    const onAbort = vi.fn();
    await postSseJson("/api/x", {}, undefined, { onAbort });
    expect(onAbort).toHaveBeenCalledTimes(1);
  });

  it("calls onAbort when read() rejects with AbortError after signal abort", async () => {
    const ac = new AbortController();
    const enc = new TextEncoder();
    const chunk = enc.encode('event: delta\ndata: {"text":"x"}\n\n');
    let reads = 0;
    const reader = {
      read: () => {
        reads += 1;
        if (reads === 1) {
          return Promise.resolve({ done: false, value: chunk });
        }
        return new Promise<ReadableStreamReadResult<Uint8Array>>((_, reject) => {
          ac.signal.addEventListener(
            "abort",
            () => reject(new DOMException("Aborted", "AbortError")),
            { once: true },
          );
        });
      },
      cancel: () => Promise.resolve(),
    };
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      status: 200,
      body: { getReader: () => reader },
    } as unknown as Response);

    const onAbort = vi.fn();
    const onDelta = vi.fn();
    const p = postSseJson("/api/x", {}, ac.signal, { onAbort, onDelta });
    await vi.waitFor(() => expect(onDelta).toHaveBeenCalled());
    ac.abort();
    await p;

    expect(onAbort).toHaveBeenCalledTimes(1);
  });
});
