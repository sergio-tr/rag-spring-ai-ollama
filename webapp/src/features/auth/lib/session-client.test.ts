import { describe, it, expect, vi, beforeEach } from "vitest";
import { commitSessionCookie, clearSessionCookie } from "./session-client";

vi.mock("@/lib/access-token", () => ({
  setAccessToken: vi.fn(),
}));

describe("session-client", () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    fetchMock.mockReset();
    globalThis.fetch = fetchMock as typeof fetch;
  });

  it("commitSessionCookie posts tokens and sets access token on success", async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { status: 200 }));
    const { setAccessToken } = await import("@/lib/access-token");
    await commitSessionCookie({ accessToken: "a", refreshToken: "r" });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/auth/session",
      expect.objectContaining({ method: "POST" }),
    );
    expect(setAccessToken).toHaveBeenCalledWith("a");
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
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/logout", expect.any(Object));
  });
});
