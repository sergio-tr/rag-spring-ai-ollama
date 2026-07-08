import { describe, expect, it, vi } from "vitest";
import { hardNavigate } from "./hard-navigation";

describe("hardNavigate", () => {
  it("assigns a locale-prefixed path when running in a browser", () => {
    const assign = vi.fn();
    vi.stubGlobal("window", { location: { assign } });

    hardNavigate("/login", "en");

    expect(assign).toHaveBeenCalledWith("/en/login");
    vi.unstubAllGlobals();
  });

  it("no-ops when window is undefined", () => {
    vi.stubGlobal("window", undefined);
    expect(() => hardNavigate("/login", "en")).not.toThrow();
    vi.unstubAllGlobals();
  });
});
