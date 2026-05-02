import { describe, it, expect, vi, beforeEach } from "vitest";
import { commitSessionCookie, clearSessionCookie } from "./session-client";
import { authApiPath } from "@/lib/api-client";

const schedulerMocks = vi.hoisted(() => ({
  scheduleAccessTokenRefreshFromJwt: vi.fn(),
  clearScheduledAccessTokenRefresh: vi.fn(),
}));

vi.mock("@/lib/auth-access-scheduler", () => ({
  scheduleAccessTokenRefreshFromJwt: schedulerMocks.scheduleAccessTokenRefreshFromJwt,
  clearScheduledAccessTokenRefresh: schedulerMocks.clearScheduledAccessTokenRefresh,
}));

vi.mock("@/lib/access-token", () => ({
  setAccessToken: vi.fn(),
}));

describe("session-client", () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    fetchMock.mockReset();
    globalThis.fetch = fetchMock as typeof fetch;
    schedulerMocks.scheduleAccessTokenRefreshFromJwt.mockClear();
    schedulerMocks.clearScheduledAccessTokenRefresh.mockClear();
  });

  it("commitSessionCookie posts tokens and sets access token on success", async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { status: 200 }));
    const { setAccessToken } = await import("@/lib/access-token");
    await commitSessionCookie({ accessToken: "a", refreshToken: "r" });
    expect(fetchMock).toHaveBeenCalledWith(
      authApiPath("/session"),
      expect.objectContaining({ method: "POST" }),
    );
    expect(setAccessToken).toHaveBeenCalledWith("a");
    expect(schedulerMocks.scheduleAccessTokenRefreshFromJwt).toHaveBeenCalledWith("a");
  });

  it("commitSessionCookie throws when session route fails", async () => {
    fetchMock.mockResolvedValueOnce(new Response("", { status: 500 }));
    await expect(commitSessionCookie({ accessToken: "a" })).rejects.toThrow("session_cookie_failed");
  });

  it("clearSessionCookie clears token and calls logout", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 200 }));
    const { setAccessToken } = await import("@/lib/access-token");
    await clearSessionCookie();
    expect(setAccessToken).toHaveBeenCalledWith(null);
    expect(schedulerMocks.clearScheduledAccessTokenRefresh).toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledWith(authApiPath("/logout"), expect.any(Object));
  });
});
