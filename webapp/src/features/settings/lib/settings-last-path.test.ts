import { afterEach, describe, expect, it, vi } from "vitest";
import {
  persistSettingsPath,
  readPersistedSettingsPath,
  SETTINGS_LAST_PATH_STORAGE_KEY,
  SETTINGS_LAST_PATH_UPDATE_EVENT,
} from "./settings-last-path";

describe("settings-last-path storage helpers", () => {
  afterEach(() => {
    sessionStorage.clear();
  });

  it("persists only locale-neutral settings paths", () => {
    persistSettingsPath("/chat");
    expect(sessionStorage.getItem(SETTINGS_LAST_PATH_STORAGE_KEY)).toBeNull();

    persistSettingsPath("/settings/account");
    expect(sessionStorage.getItem(SETTINGS_LAST_PATH_STORAGE_KEY)).toBe("/settings/account");

    persistSettingsPath("/settings");
    expect(sessionStorage.getItem(SETTINGS_LAST_PATH_STORAGE_KEY)).toBe("/settings");
  });

  it("readPersistedSettingsPath rejects corrupted values", () => {
    sessionStorage.setItem(SETTINGS_LAST_PATH_STORAGE_KEY, "/en/settings/account");
    expect(readPersistedSettingsPath()).toBeNull();
  });

  it("dispatches SETTINGS_LAST_PATH_UPDATE_EVENT when persisting", () => {
    const listener = vi.fn();
    window.addEventListener(SETTINGS_LAST_PATH_UPDATE_EVENT, listener);
    persistSettingsPath("/settings/user");
    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(SETTINGS_LAST_PATH_UPDATE_EVENT, listener);
  });
});
