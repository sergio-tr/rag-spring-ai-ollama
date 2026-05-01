import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import {
  SETTINGS_LAST_PATH_STORAGE_KEY,
  SETTINGS_LAST_PATH_UPDATE_EVENT,
} from "@/features/settings/lib/settings-last-path";
import { useSettingsSidebarHref } from "./use-settings-sidebar-href";

const mockUsePathname = vi.fn(() => "/projects");

vi.mock("@/navigation", () => ({
  usePathname: () => mockUsePathname(),
}));

describe("useSettingsSidebarHref", () => {
  beforeEach(() => {
    sessionStorage.clear();
    mockUsePathname.mockReturnValue("/projects");
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it("restores persisted settings path when outside settings", async () => {
    sessionStorage.setItem(SETTINGS_LAST_PATH_STORAGE_KEY, "/settings/account");
    const { result } = renderHook(() => useSettingsSidebarHref());
    await waitFor(() => {
      expect(result.current).toBe("/settings/account");
    });
  });

  it("uses live pathname while user is under settings", () => {
    mockUsePathname.mockReturnValue("/settings/data");
    const { result } = renderHook(() => useSettingsSidebarHref());
    expect(result.current).toBe("/settings/data");
  });

  it("updates footer href after SETTINGS_LAST_PATH_UPDATE_EVENT while elsewhere", async () => {
    mockUsePathname.mockReturnValue("/chat");
    const { result } = renderHook(() => useSettingsSidebarHref());
    expect(result.current).toBe("/settings");

    sessionStorage.setItem(SETTINGS_LAST_PATH_STORAGE_KEY, "/settings/presets");
    window.dispatchEvent(new Event(SETTINGS_LAST_PATH_UPDATE_EVENT));

    await waitFor(() => {
      expect(result.current).toBe("/settings/presets");
    });
  });

  it("never produces double locale segments", async () => {
    mockUsePathname.mockReturnValue("/projects");
    sessionStorage.setItem(SETTINGS_LAST_PATH_STORAGE_KEY, "/settings/account");
    const { result } = renderHook(() => useSettingsSidebarHref());

    await waitFor(() => {
      expect(result.current).toMatch(/^\/settings(\/.*)?$/);
      expect(result.current).not.toMatch(/\/en\/en/);
    });
  });
});
