import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  ApiError,
  apiFetch,
  apiProductPath,
  getApiBaseUrl,
  getRagApiProductPrefix,
  onApiUnauthorized,
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
      if (url.includes("/api/auth/refresh")) {
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
    expect(getApiBaseUrl()).toMatch(/^https?:\/\//);
  });

  it("builds apiProductPath without leading slash on argument", () => {
    expect(apiProductPath("projects")).toBe(`${getRagApiProductPrefix()}/projects`);
  });

  it("ignores listener errors in notifyUnauthorized", async () => {
    vi.spyOn(accessToken, "getAccessToken").mockReturnValue("tok");
    onApiUnauthorized(() => {
      throw new Error("listener boom");
    });
    vi.mocked(globalThis.fetch).mockImplementation((input: RequestInfo | URL) => {
      if (String(input).includes("/api/auth/refresh")) {
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
      if (url.includes("/api/auth/refresh")) {
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
      if (String(input).includes("/api/auth/refresh")) {
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
      if (String(input).includes("/api/auth/refresh")) {
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
});
