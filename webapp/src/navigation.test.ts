import { describe, it, expect, vi } from "vitest";

vi.mock("next-intl/navigation", () => ({
  createNavigation: () => ({
    Link: () => null,
    redirect: vi.fn(),
    usePathname: () => "/en",
    useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
    getPathname: vi.fn(() => "/en"),
  }),
}));

import { Link, redirect, usePathname, useRouter, getPathname } from "./navigation";

describe("navigation (next-intl)", () => {
  it("exports helpers from createNavigation", () => {
    expect(Link).toBeDefined();
    expect(redirect).toBeDefined();
    expect(usePathname).toBeDefined();
    expect(useRouter).toBeDefined();
    expect(getPathname).toBeDefined();
  });
});
