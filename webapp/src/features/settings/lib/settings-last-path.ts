export const SETTINGS_LAST_PATH_STORAGE_KEY = "settings:last-pathname";

/** Dispatched after sessionStorage is updated so sidebar can sync href without polling. */
export const SETTINGS_LAST_PATH_UPDATE_EVENT = "settings-last-path-update";

export function persistSettingsPath(pathname: string): void {
  if (typeof window === "undefined") return;
  if (pathname !== "/settings" && !pathname.startsWith("/settings/")) return;
  try {
    sessionStorage.setItem(SETTINGS_LAST_PATH_STORAGE_KEY, pathname);
    window.dispatchEvent(new Event(SETTINGS_LAST_PATH_UPDATE_EVENT));
  } catch {
    // Ignore quota / private mode.
  }
}

export function readPersistedSettingsPath(): string | null {
  if (typeof window === "undefined") return null;
  try {
    const value = sessionStorage.getItem(SETTINGS_LAST_PATH_STORAGE_KEY);
    if (!value) return null;
    if (value === "/settings" || value.startsWith("/settings/")) return value;
    return null;
  } catch {
    return null;
  }
}
