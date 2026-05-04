import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  ApiError,
  authApiPath,
  apiDownloadBlob,
  apiFetch,
  apiProductPath,
  createHttpApiError,
  getApiBaseUrl,
  getRagApiProductPrefix,
  getSafeApiErrorMessage,
  onApiUnauthorized,
  resolveBrowserProductApiUrl,
  sanitizePlainErrorTextForUi,
} from "./api-client";
import * as accessToken from "./access-token";

describe("apiFetch", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        void input;
        return Promise.reject(new Error("unmocked fetch"));
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("returns JSON body on 200", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "application/json" }),
      text: () => Promise.resolve(""),
      json: () => Promise.resolve({ hello: "world" }),
    } as Response);

    const body = await apiFetch<{ hello: string }>(apiProductPath("/projects"), { skipCredentials: true });
    expect(body.hello).toBe("world");
  });

  it("retries once after 401 when refresh returns a new token", async () => {
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue("old");
    const setSpy = vi.spyOn(accessToken, "setAccessToken").mockImplementation(() => {});

    const failOnce = vi
      .fn()
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        headers: new Headers(),
        text: () => Promise.resolve(""),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        text: () => Promise.resolve(""),
        json: () => Promise.resolve({ ok: true }),
      } as Response);

    vi.mocked(globalThis.fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes(authApiPath("/refresh"))) {
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: new Headers({ "content-type": "application/json" }),
          text: () => Promise.resolve(""),
          json: () => Promise.resolve({ accessToken: "new-token" }),
        } as Response);
      }
      return failOnce();
    });

    const body = await apiFetch<{ ok: boolean }>(apiProductPath("/projects"));
    expect(body.ok).toBe(true);
    expect(setSpy).toHaveBeenCalledWith("new-token");
    expect(failOnce).toHaveBeenCalledTimes(2);
  });

  it("throws ApiError when response is not ok", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 503,
      headers: new Headers(),
      text: () => Promise.resolve("unavailable"),
    } as Response);

    await expect(apiFetch("/x", { skipCredentials: true })).rejects.toThrow(ApiError);
  });

  it("returns undefined on 204", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      status: 204,
      headers: new Headers(),
      text: () => Promise.resolve(""),
    } as Response);

    const body = await apiFetch<undefined>("/api/x", { skipCredentials: true });
    expect(body).toBeUndefined();
  });

  it("returns text when content-type is not JSON", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "text/plain" }),
      text: () => Promise.resolve("plain-body"),
      json: () => Promise.reject(new Error("not json")),
    } as Response);

    const body = await apiFetch<string>("/api/x", { skipCredentials: true });
    expect(body).toBe("plain-body");
  });

  it("drops Content-Type for FormData so the browser sets multipart boundary", async () => {
    const fd = new FormData();
    vi.mocked(globalThis.fetch).mockImplementation((_url, init) => {
      const headers = new Headers(init?.headers as HeadersInit | undefined);
      expect(headers.has("Content-Type")).toBe(false);
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        text: () => Promise.resolve(""),
        json: () => Promise.resolve({ ok: true }),
      } as Response);
    });

    await apiFetch("/api/upload", { method: "POST", body: fd, skipCredentials: true });
  });

  it("uses absolute URL when path is http(s)", async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      expect(String(input)).toBe("https://other.example/z");
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        text: () => Promise.resolve(""),
        json: () => Promise.resolve({}),
      } as Response);
    });

    await apiFetch("https://other.example/z", { skipCredentials: true });
  });

  it("exposes product prefix and API base getters", () => {
    expect(getRagApiProductPrefix()).toMatch(/^\//);
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://localhost:9000");
    expect(getApiBaseUrl()).toBe("http://localhost:9000");
    vi.unstubAllEnvs();
  });

  it("resolveBrowserProductApiUrl keeps same-origin path when base URL unset", () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "");
    expect(resolveBrowserProductApiUrl("/api/v5/projects")).toBe("/api/v5/projects");
    expect(resolveBrowserProductApiUrl("relative")).toBe("/relative");
    vi.unstubAllEnvs();
  });

  it("resolveBrowserProductApiUrl prefixes trimmed absolute base", () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://127.0.0.1:9000/");
    expect(resolveBrowserProductApiUrl("/api/v5/me")).toBe("http://127.0.0.1:9000/api/v5/me");
    vi.unstubAllEnvs();
  });

  it("builds apiProductPath without leading slash on argument", () => {
    expect(apiProductPath("projects")).toBe(`${getRagApiProductPrefix()}/projects`);
  });

  it("authApiPath places auth segment under product prefix", () => {
    expect(authApiPath("/refresh")).toBe(`${getRagApiProductPrefix()}/auth/refresh`);
    expect(authApiPath("whoami")).toBe(`${getRagApiProductPrefix()}/auth/whoami`);
  });

  it("ignores listener errors in notifyUnauthorized", async () => {
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue("tok");
    onApiUnauthorized(() => {
      throw new Error("listener boom");
    });
    vi.mocked(globalThis.fetch).mockImplementation((input: RequestInfo | URL) => {
      if (String(input).includes(authApiPath("/refresh"))) {
        return Promise.resolve({
          ok: false,
          status: 401,
          headers: new Headers(),
          text: () => Promise.resolve(""),
        } as Response);
      }
      return Promise.resolve({
        ok: false,
        status: 401,
        headers: new Headers(),
        text: () => Promise.resolve("nope"),
      } as Response);
    });

    await expect(apiFetch(apiProductPath("/fail"))).rejects.toThrow(ApiError);
  });

  it("notifies unauthorized listeners on 401 when refresh fails", async () => {
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue("tok");
    const listener = vi.fn();
    const off = onApiUnauthorized(listener);

    vi.mocked(globalThis.fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes(authApiPath("/refresh"))) {
        return Promise.resolve({
          ok: false,
          status: 401,
          headers: new Headers(),
          text: () => Promise.resolve(""),
        } as Response);
      }
      return Promise.resolve({
        ok: false,
        status: 401,
        headers: new Headers(),
        text: () => Promise.resolve("denied"),
      } as Response);
    });

    await expect(apiFetch(apiProductPath("/x"))).rejects.toThrow(ApiError);
    expect(listener).toHaveBeenCalled();
    off();
  });

  it("unsubscribes onApiUnauthorized", () => {
    const fn = vi.fn();
    const off = onApiUnauthorized(fn);
    off();
    expect(fn).not.toHaveBeenCalled();
  });

  it("skips traceparent when skipTraceparent is true", async () => {
    vi.mocked(globalThis.fetch).mockImplementation((_url, init) => {
      const headers = new Headers(init?.headers as HeadersInit | undefined);
      expect(headers.has("traceparent")).toBe(false);
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        text: () => Promise.resolve(""),
        json: () => Promise.resolve({}),
      } as Response);
    });

    await apiFetch("/z", { skipCredentials: true, skipTraceparent: true });
  });

  it("does not retry refresh when skipCredentials", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 401,
      headers: new Headers(),
      text: () => Promise.resolve("x"),
    } as Response);

    await expect(apiFetch("/x", { skipCredentials: true })).rejects.toThrow(ApiError);
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  });

  it("returns false from refresh when refresh throws", async () => {
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue("tok");
    vi.mocked(globalThis.fetch).mockImplementation((input: RequestInfo | URL) => {
      if (String(input).includes(authApiPath("/refresh"))) {
        return Promise.reject(new Error("network"));
      }
      return Promise.resolve({
        ok: false,
        status: 401,
        headers: new Headers(),
        text: () => Promise.resolve(""),
      } as Response);
    });

    await expect(apiFetch(apiProductPath("/p"))).rejects.toThrow();
  });

  it("maps aborted request to ApiError with abort kind", async () => {
    vi.mocked(globalThis.fetch).mockRejectedValueOnce(new DOMException("aborted", "AbortError"));

    await expect(apiFetch("/aborted", { skipCredentials: true })).rejects.toMatchObject({
      status: 0,
      meta: expect.objectContaining({ kind: "abort" }),
    });
  });

  it("refresh ok without accessToken in body still retries main request", async () => {
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue("old");
    const main = vi
      .fn()
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        headers: new Headers(),
        text: () => Promise.resolve(""),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        text: () => Promise.resolve(""),
        json: () => Promise.resolve({ data: true }),
      } as Response);

    vi.mocked(globalThis.fetch).mockImplementation((input: RequestInfo | URL) => {
      if (String(input).includes(authApiPath("/refresh"))) {
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: new Headers({ "content-type": "application/json" }),
          text: () => Promise.resolve(""),
          json: () => Promise.resolve({}),
        } as Response);
      }
      return main();
    });

    const body = await apiFetch<{ data: boolean }>(apiProductPath("/y"));
    expect(body.data).toBe(true);
    expect(main).toHaveBeenCalledTimes(2);
  });

  it("extracts detail from JSON error body on 400", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 400,
      headers: new Headers({ "content-type": "application/json" }),
      text: () => Promise.resolve(JSON.stringify({ detail: "Invalid payload" })),
    } as Response);
    await expect(apiFetch("/bad", { skipCredentials: true })).rejects.toMatchObject({
      status: 400,
      message: "Invalid payload",
    });
  });

  it("getSafeApiErrorMessage handles ApiError and generic errors", () => {
    expect(getSafeApiErrorMessage(new ApiError(418, "tea"))).toBe("tea");
    expect(getSafeApiErrorMessage(new Error("e"))).toBe("Unexpected error. Please try again.");
    expect(getSafeApiErrorMessage(12)).toBe("12");
  });

  it("uses short non-HTML text for client errors when body is small", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 422,
      statusText: "Unprocessable",
      headers: new Headers({ "content-type": "text/plain" }),
      text: () => Promise.resolve("bad schema"),
    } as Response);
    try {
      await apiFetch("/boom", { skipCredentials: true });
      expect.fail();
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      expect((e as ApiError).message).toMatch(/bad schema/);
    }
  });

  it("classifies JSON-looking error body without JSON content-type", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 422,
      statusText: "Unprocessable",
      headers: new Headers(),
      text: () => Promise.resolve('{"message":"nope"}'),
    } as Response);
    try {
      await apiFetch("/v", { skipCredentials: true });
      expect.fail();
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      expect((e as ApiError).meta?.kind).toBe("http");
      expect((e as ApiError).message).toContain("nope");
    }
  });

  it("maps 502 to a safe message when error response body read fails", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 502,
      statusText: "Bad Gateway",
      headers: new Headers(),
      text: () => Promise.reject(new Error("read failed")),
    } as Response);

    try {
      await apiFetch("/z", { skipCredentials: true });
      expect.fail("expected throw");
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      const err = e as ApiError;
      expect(err.status).toBe(502);
      expect(err.message).not.toContain("<html");
      expect(err.meta?.kind).toBeDefined();
    }
  });

  it("normalizes nginx HTML 502 without exposing HTML in message", async () => {
    const html = "<html><body>502 Bad Gateway</body></html>";
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 502,
      headers: new Headers({ "content-type": "text/html" }),
      text: () => Promise.resolve(html),
    } as Response);

    try {
      await apiFetch("/api/x", { skipCredentials: true });
      expect.fail("expected throw");
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      const err = e as ApiError;
      expect(err.message).not.toContain("<html");
      expect(err.meta?.kind).toBe("http");
    }
  });

  it("prefixes path without leading slash onto API base", async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      expect(String(input)).toContain("/relative/path");
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        text: () => Promise.resolve(""),
        json: () => Promise.resolve({}),
      } as Response);
    });

    await apiFetch("relative/path", { skipCredentials: true });
  });

  it("does not override existing Authorization header", async () => {
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue("from-store");
    vi.mocked(globalThis.fetch).mockImplementation((_url, init) => {
      const headers = new Headers(init?.headers as HeadersInit | undefined);
      expect(headers.get("Authorization")).toBe("Bearer preset");
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        text: () => Promise.resolve(""),
        json: () => Promise.resolve({}),
      } as Response);
    });

    await apiFetch("/x", {
      headers: new Headers({ Authorization: "Bearer preset" }),
    });
  });

  it("falls back to generic request failed message for large plain text body", async () => {
    const longBody = "x".repeat(1000);
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 429,
      statusText: "Too Many Requests",
      headers: new Headers({ "content-type": "text/plain" }),
      text: () => Promise.resolve(longBody),
    } as Response);

    await expect(apiFetch("/too-many", { skipCredentials: true })).rejects.toMatchObject({
      status: 429,
      message: "Request failed (429).",
    });
  });

  it("normalizes non-json 500 to generic server message", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 500,
      headers: new Headers({ "content-type": "text/plain" }),
      text: () => Promise.resolve("internal details"),
    } as Response);

    await expect(apiFetch("/internal", { skipCredentials: true })).rejects.toMatchObject({
      status: 500,
      message: "Server error. Please try again.",
    });
  });
});

describe("createHttpApiError", () => {
  it("extracts validation details from errors array", () => {
    const err = createHttpApiError({
      status: 400,
      bodyText: JSON.stringify({ errors: ["one", "two"] }),
      headers: new Headers({ "content-type": "application/json" }),
      requestUrl: "http://example.test/api/x",
      method: "POST",
    });
    expect(err.meta?.details).toEqual({ errors: ["one", "two"] });
    expect(err.meta?.parsedJson).toEqual({ errors: ["one", "two"] });
  });

  it("uses plain-text fallback when JSON content-type body is invalid JSON", () => {
    const err = createHttpApiError({
      status: 422,
      bodyText: "{broken",
      headers: new Headers({ "content-type": "application/json" }),
      requestUrl: "http://example.test/api/x",
      method: "POST",
    });
    expect(err.message).toContain("{broken");
    expect(err.meta?.kind).toBe("http");
  });

  it("returns Not found for 404 responses", () => {
    const err = createHttpApiError({
      status: 404,
      bodyText: "",
      headers: new Headers(),
      requestUrl: "http://example.test/api/missing",
      method: "GET",
    });
    expect(err.message).toMatch(/not found/i);
  });

  it("extracts validation details from fieldErrors", () => {
    const err = createHttpApiError({
      status: 400,
      bodyText: JSON.stringify({ message: "bad request", fieldErrors: { topK: "must be >=1" } }),
      headers: new Headers({ "content-type": "application/json", "x-request-id": "req-1" }),
      requestUrl: "http://example.test/api/config",
      method: "PUT",
    });
    expect(err.meta?.details).toEqual({ fieldErrors: { topK: "must be >=1" } });
    expect(err.meta?.diagnostics?.requestId).toBe("req-1");
  });

  it("maps html-like body to gateway message for 503", () => {
    const err = createHttpApiError({
      status: 503,
      bodyText: "<!doctype html><html><body>down</body></html>",
      headers: new Headers({ "content-type": "text/plain" }),
      requestUrl: "http://example.test/api/projects",
      method: "GET",
    });
    expect(err.message.toLowerCase()).toContain("gateway");
    expect(err.meta?.rawBodyPreview).toContain("<!doctype html>");
  });
});

describe("apiDownloadBlob", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(new Error("unmocked fetch"))),
    );
  });

  it("wraps network failures as ApiError with kind network", async () => {
    vi.mocked(globalThis.fetch).mockRejectedValueOnce(new TypeError("offline"));
    await expect(apiDownloadBlob("/blob", { skipCredentials: true })).rejects.toMatchObject({
      status: 0,
      meta: expect.objectContaining({ kind: "network" }),
    });
  });

  it("wraps aborted download as ApiError with kind abort", async () => {
    vi.mocked(globalThis.fetch).mockRejectedValueOnce(new DOMException("aborted", "AbortError"));

    await expect(apiDownloadBlob("/blob", { skipCredentials: true })).rejects.toMatchObject({
      status: 0,
      meta: expect.objectContaining({ kind: "abort" }),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("returns blob on 200", async () => {
    const blob = new Blob(["zip"], { type: "application/zip" });
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers(),
      blob: () => Promise.resolve(blob),
    } as Response);

    const out = await apiDownloadBlob("/export.zip", { skipCredentials: true });
    expect(out).toBe(blob);
  });

  it("retries after 401 when refresh succeeds", async () => {
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue("t");
    const b = new Blob(["a"]);
    const main = vi
      .fn()
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        headers: new Headers(),
        text: () => Promise.resolve(""),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers(),
        blob: () => Promise.resolve(b),
      } as Response);

    vi.mocked(globalThis.fetch).mockImplementation((input: RequestInfo | URL) => {
      if (String(input).includes(authApiPath("/refresh"))) {
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: new Headers({ "content-type": "application/json" }),
          text: () => Promise.resolve(""),
          json: () => Promise.resolve({ accessToken: "n" }),
        } as Response);
      }
      return main();
    });

    const out = await apiDownloadBlob(apiProductPath("/me/export"));
    expect(out).toBe(b);
    expect(main).toHaveBeenCalledTimes(2);
  });

  it("throws ApiError and notifies on 401 when refresh fails", async () => {
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue("t");
    const listener = vi.fn();
    onApiUnauthorized(listener);

    vi.mocked(globalThis.fetch).mockImplementation((input: RequestInfo | URL) => {
      if (String(input).includes(authApiPath("/refresh"))) {
        return Promise.resolve({
          ok: false,
          status: 401,
          headers: new Headers(),
          text: () => Promise.resolve(""),
        } as Response);
      }
      return Promise.resolve({
        ok: false,
        status: 401,
        headers: new Headers(),
        text: () => Promise.resolve("denied"),
      } as Response);
    });

    await expect(apiDownloadBlob(apiProductPath("/blob"))).rejects.toThrow(ApiError);
    expect(listener).toHaveBeenCalled();
  });

  it("skips refresh when skipCredentials", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 401,
      headers: new Headers(),
      text: () => Promise.resolve("x"),
    } as Response);

    await expect(apiDownloadBlob("/f", { skipCredentials: true })).rejects.toThrow(ApiError);
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  });

  it("omits traceparent when skipTraceparent", async () => {
    vi.mocked(globalThis.fetch).mockImplementation((_url, init) => {
      const headers = new Headers(init?.headers as HeadersInit | undefined);
      expect(headers.has("traceparent")).toBe(false);
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers(),
        blob: () => Promise.resolve(new Blob()),
      } as Response);
    });

    await apiDownloadBlob("/z", { skipCredentials: true, skipTraceparent: true });
  });

  it("uses GET method", async () => {
    vi.mocked(globalThis.fetch).mockImplementation((_url, init) => {
      expect(init?.method).toBe("GET");
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers(),
        blob: () => Promise.resolve(new Blob()),
      } as Response);
    });

    await apiDownloadBlob("/a", { skipCredentials: true });
  });
});

describe("sanitizePlainErrorTextForUi", () => {
  it("returns trimmed plain text", () => {
    expect(sanitizePlainErrorTextForUi("  hello  ", 100)).toBe("hello");
  });

  it("returns empty for HTML-looking bodies", () => {
    expect(sanitizePlainErrorTextForUi("<html><body>x</body></html>", 80)).toBe("");
  });

  it("returns empty for multi-line stack traces", () => {
    const stack = `java.lang.Error: x\n\tat com.example.A.a(A.java:1)\n\tat com.example.B.b(B.java:2)`;
    expect(sanitizePlainErrorTextForUi(stack, 200)).toBe("");
  });

  it("truncates long harmless messages", () => {
    const long = "x".repeat(400);
    expect(sanitizePlainErrorTextForUi(long, 50).length).toBeLessThanOrEqual(52);
  });
});
