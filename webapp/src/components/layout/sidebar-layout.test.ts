import { describe, it, expect } from "vitest";
import {
  clampSidebarWidth,
  DEFAULT_SIDEBAR_WIDTH_PX,
  MIN_SIDEBAR_WIDTH_PX,
  maxSidebarWidthForViewport,
} from "./sidebar-layout";

describe("clampSidebarWidth", () => {
  it("clamps below minimum up to MIN_SIDEBAR_WIDTH_PX", () => {
    expect(clampSidebarWidth(80, 1200)).toBe(MIN_SIDEBAR_WIDTH_PX);
  });

  it("clamps above ~33% viewport down", () => {
    const vw = 900;
    const maxW = maxSidebarWidthForViewport(vw);
    expect(clampSidebarWidth(400, vw)).toBe(maxW);
    expect(maxW).toBe(Math.floor(vw * 0.33));
  });

  it("accepts mid-range values", () => {
    expect(clampSidebarWidth(260, 1200)).toBe(260);
  });

  it("falls back for non-finite input", () => {
    expect(clampSidebarWidth(Number.NaN, 1000)).toBe(DEFAULT_SIDEBAR_WIDTH_PX);
  });
});
