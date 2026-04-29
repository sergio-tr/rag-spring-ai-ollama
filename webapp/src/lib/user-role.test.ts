import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { getStoredUserRole, setStoredUserRole } from "./user-role";

describe("user-role", () => {
  const originalSessionStorage = globalThis.sessionStorage;
  const originalWindow = (globalThis as unknown as { window?: unknown }).window;

  beforeEach(() => {
    // Ensure a clean storage between tests.
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    globalThis.sessionStorage = originalSessionStorage;
    (globalThis as unknown as { window?: unknown }).window = originalWindow;
  });

  it("getStoredUserRole returns null for unknown values", () => {
    sessionStorage.setItem("rag_user_role", "NOT_A_ROLE");
    expect(getStoredUserRole()).toBeNull();
  });

  it("getStoredUserRole returns USER/ADMIN when stored", () => {
    sessionStorage.setItem("rag_user_role", "USER");
    expect(getStoredUserRole()).toBe("USER");
    sessionStorage.setItem("rag_user_role", "ADMIN");
    expect(getStoredUserRole()).toBe("ADMIN");
  });

  it("getStoredUserRole returns null when sessionStorage throws", () => {
    const getItem = vi.fn(() => {
      throw new Error("blocked");
    });
    globalThis.sessionStorage = { ...sessionStorage, getItem } as unknown as Storage;
    expect(getStoredUserRole()).toBeNull();
  });

  it("setStoredUserRole sets or removes the key", () => {
    setStoredUserRole("USER");
    expect(sessionStorage.getItem("rag_user_role")).toBe("USER");
    setStoredUserRole(null);
    expect(sessionStorage.getItem("rag_user_role")).toBeNull();
  });

  it("setStoredUserRole ignores storage errors", () => {
    const setItem = vi.fn(() => {
      throw new Error("quota");
    });
    globalThis.sessionStorage = { ...sessionStorage, setItem } as unknown as Storage;
    expect(() => setStoredUserRole("ADMIN")).not.toThrow();
  });

  it("returns null and no-ops when window is undefined (SSR guard)", () => {
    (globalThis as unknown as { window?: unknown }).window = undefined;
    expect(getStoredUserRole()).toBeNull();
    expect(() => setStoredUserRole("USER")).not.toThrow();
    expect(() => setStoredUserRole(null)).not.toThrow();
  });
});

