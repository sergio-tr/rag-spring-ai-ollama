import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest, NextResponse } from "next/server";
import { AUTH_ACCESS_COOKIE_NAME } from "@/lib/auth-cookie";

const handleI18n = vi.fn(() => NextResponse.next());

vi.mock("next-intl/middleware", () => ({
  default: vi.fn(() => handleI18n),
}));

describe("proxy middleware", () => {
  beforeEach(() => {
    vi.resetModules();
    handleI18n.mockImplementation(() => NextResponse.next());
    delete process.env.NEXT_PUBLIC_SKIP_AUTH;
  });

  it("delegates to i18n middleware when NEXT_PUBLIC_SKIP_AUTH is true", async () => {
    process.env.NEXT_PUBLIC_SKIP_AUTH = "true";
    const { default: proxy } = await import("./proxy");
    const req = new NextRequest(new URL("http://localhost/en/chat"));
    const res = proxy(req);
    expect(handleI18n).toHaveBeenCalledWith(req);
    expect(res.status).toBe(200);
  });

  it("redirects unauthenticated users from app routes to locale login", async () => {
    const { default: proxy } = await import("./proxy");
    const req = new NextRequest(new URL("http://localhost/en/chat"));
    const res = proxy(req);
    expect(res.status).toBe(307);
    expect(res.headers.get("location")).toBe("http://localhost/en/login");
  });

  it("allows app routes when access cookie is present", async () => {
    const { default: proxy } = await import("./proxy");
    const req = new NextRequest(new URL("http://localhost/en/projects"));
    // NextRequest cookie parsing can vary by runtime; set it explicitly.
    req.cookies.set(AUTH_ACCESS_COOKIE_NAME, "tok");
    const res = proxy(req);
    expect(handleI18n).toHaveBeenCalled();
    expect(res.status).toBe(200);
  });

  it("uses default locale in redirect when path has no locale prefix", async () => {
    const { default: proxy } = await import("./proxy");
    const req = new NextRequest(new URL("http://localhost/chat"));
    const res = proxy(req);
    expect(res.headers.get("location")).toMatch(/\/en\/login$/);
  });

  it("does not redirect non-app routes without auth cookie", async () => {
    const { default: proxy } = await import("./proxy");
    const req = new NextRequest(new URL("http://localhost/"));
    const res = proxy(req);
    expect(res.status).toBe(200);
    expect(handleI18n).toHaveBeenCalledWith(req);
  });

  it.each(["/documents", "/settings", "/lab", "/admin"] as const)(
    "redirects unauthenticated users from %s",
    async (path) => {
      const { default: proxy } = await import("./proxy");
      const req = new NextRequest(new URL(`http://localhost/en${path}`));
      const res = proxy(req);
      expect(res.status).toBe(307);
      expect(res.headers.get("location")).toBe("http://localhost/en/login");
    },
  );
});
