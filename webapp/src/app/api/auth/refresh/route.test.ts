import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { POST } from "./route";

const cookiesMock = vi.fn();

vi.mock("next/headers", () => ({
  cookies: () => cookiesMock(),
}));

describe("POST refresh route", () => {
  const prevFetch = globalThis.fetch;
  const prevApi = process.env.NEXT_PUBLIC_API_BASE_URL;
  const prevE2e = process.env.E2E_ALLOW_INSECURE_COOKIES;

  beforeEach(() => {
    cookiesMock.mockResolvedValue({
      get: () => ({ value: "refresh-token" }),
    });
    process.env.NEXT_PUBLIC_API_BASE_URL = "http://api.test";
    delete process.env.E2E_ALLOW_INSECURE_COOKIES;
  });

  afterEach(() => {
    globalThis.fetch = prevFetch;
    if (prevApi === undefined) delete process.env.NEXT_PUBLIC_API_BASE_URL;
    else process.env.NEXT_PUBLIC_API_BASE_URL = prevApi;
    if (prevE2e === undefined) delete process.env.E2E_ALLOW_INSECURE_COOKIES;
    else process.env.E2E_ALLOW_INSECURE_COOKIES = prevE2e;
    vi.restoreAllMocks();
  });

  it("returns 401 when refresh cookie is missing", async () => {
    cookiesMock.mockResolvedValueOnce({ get: () => undefined });
    const res = await POST();
    expect(res.status).toBe(401);
  });

  it("returns upstream status when refresh fails", async () => {
    globalThis.fetch = vi.fn(async () =>
      Response.json({ error: "x" }, { status: 503 }),
    ) as typeof fetch;
    const res = await POST();
    expect(res.status).toBe(503);
  });

  it("returns 502 when accessToken missing in upstream body", async () => {
    globalThis.fetch = vi.fn(async () => Response.json({})) as typeof fetch;
    const res = await POST();
    expect(res.status).toBe(502);
  });

  it("sets cookies on success", async () => {
    globalThis.fetch = vi.fn(async () =>
      Response.json({
        accessToken: "new-access",
        refreshToken: "new-refresh",
      }),
    ) as typeof fetch;
    const res = await POST();
    expect(res.status).toBe(200);
    const body = (await res.json()) as { ok: boolean; accessToken: string };
    expect(body.ok).toBe(true);
    expect(body.accessToken).toBe("new-access");
  });
});
