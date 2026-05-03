export const SETTINGS_LAST_PATH_STORAGE_KEY = "settings:last-pathname";

/** Dispatched after sessionStorage is updated so sidebar can sync href without polling. */
export const SETTINGS_LAST_PATH_UPDATE_EVENT = "settings-last-path-update";

export function persistSettingsPath(pathname: string): void {
  if (globalThis.window === undefined) return;
  if (pathname !== "/settings" && !pathname.startsWith("/settings/")) return;
  try {
    globalThis.sessionStorage.setItem(SETTINGS_LAST_PATH_STORAGE_KEY, pathname);
    globalThis.window.dispatchEvent(new Event(SETTINGS_LAST_PATH_UPDATE_EVENT));
  } catch {
    // Ignore quota / private mode.
  }
}

export function readPersistedSettingsPath(): string | null {
  if (globalThis.window === undefined) return null;
  try {
    const value = globalThis.sessionStorage.getItem(SETTINGS_LAST_PATH_STORAGE_KEY);
    if (!value) return null;
    if (value === "/settings" || value.startsWith("/settings/")) return value;
    return null;
  } catch {
    return null;
  }
}
