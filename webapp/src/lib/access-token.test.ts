import { afterEach, describe, expect, it, vi } from "vitest";

import { getAccessToken, setAccessToken } from "./access-token";

describe("access-token", () => {
  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("returns null and no-ops when window is undefined (SSR)", () => {
    vi.stubGlobal("window", undefined as unknown as Window);
    expect(getAccessToken()).toBeNull();
    expect(() => setAccessToken("abc")).not.toThrow();
    expect(() => setAccessToken(null)).not.toThrow();
  });

  it("returns null when getItem throws (private mode / quota)", () => {
    const spy = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(getAccessToken()).toBeNull();
    spy.mockRestore();
  });

  it("ignores setItem failure when clearing", () => {
    vi.spyOn(Storage.prototype, "removeItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(() => setAccessToken(null)).not.toThrow();
  });

  it("ignores setItem failure when storing token", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("quota");
    });
    expect(() => setAccessToken("tok")).not.toThrow();
  });

  it("returns null when empty", () => {
    expect(getAccessToken()).toBeNull();
  });

  it("round-trips token via sessionStorage", () => {
    setAccessToken("abc");
    expect(getAccessToken()).toBe("abc");
  });

  it("clears token when set to null", () => {
    setAccessToken("x");
    setAccessToken(null);
    expect(getAccessToken()).toBeNull();
  });
});
