import { describe, it, expect } from "vitest";
import { POST } from "./route";

describe("POST /api/auth/session", () => {
  it("returns 400 on invalid JSON", async () => {
    const req = new Request("http://localhost/api/auth/session", {
      method: "POST",
      body: "not-json",
    });
    const res = await POST(req);
    expect(res.status).toBe(400);
  });

  it("returns 400 when accessToken is missing", async () => {
    const req = new Request("http://localhost/api/auth/session", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({}),
    });
    const res = await POST(req);
    expect(res.status).toBe(400);
  });

  it("sets access cookie and optional refresh", async () => {
    const req = new Request("http://localhost/api/auth/session", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ accessToken: "a", refreshToken: "r" }),
    });
    const res = await POST(req);
    expect(res.status).toBe(200);
    const data = (await res.json()) as { ok: boolean };
    expect(data.ok).toBe(true);
    expect(res.headers.getSetCookie?.()?.length ?? res.headers.get("set-cookie")).toBeTruthy();
  });
});
