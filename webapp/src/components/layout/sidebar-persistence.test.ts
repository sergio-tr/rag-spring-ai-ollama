import { describe, it, expect, beforeEach } from "vitest";
import {
  patchSidebarPersistence,
  readSidebarPersistence,
  SIDEBAR_STORAGE_KEY,
} from "./sidebar-persistence";

describe("sidebar-persistence", () => {
  beforeEach(() => {
    localStorage.removeItem(SIDEBAR_STORAGE_KEY);
  });

  it("patchSidebarPersistence merges shell fields without losing projects state", () => {
    localStorage.setItem(
      SIDEBAR_STORAGE_KEY,
      JSON.stringify({ projectsCollapsed: true, expandedProjectIds: ["a"], shellCollapsed: false }),
    );
    patchSidebarPersistence({ shellCollapsed: true, sidebarWidthPx: 280 });
    const next = readSidebarPersistence();
    expect(next.projectsCollapsed).toBe(true);
    expect(next.expandedProjectIds).toEqual(["a"]);
    expect(next.shellCollapsed).toBe(true);
    expect(next.sidebarWidthPx).toBe(280);
  });

  it("readSidebarPersistence returns defaults when storage empty", () => {
    const p = readSidebarPersistence();
    expect(p.projectsCollapsed).toBe(false);
    expect(p.expandedProjectIds).toEqual([]);
    expect(p.shellCollapsed).toBe(false);
  });
});
