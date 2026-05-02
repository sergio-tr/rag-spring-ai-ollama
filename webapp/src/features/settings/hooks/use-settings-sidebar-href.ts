"use client";

import { usePathname } from "@/navigation";
import { useSyncExternalStore } from "react";
import {
  readPersistedSettingsPath,
  SETTINGS_LAST_PATH_UPDATE_EVENT,
} from "@/features/settings/lib/settings-last-path";

const DEFAULT_SETTINGS_HREF = "/settings";

function isUnderSettings(path: string): boolean {
  return path === "/settings" || path.startsWith("/settings/");
}

function subscribe(onStoreChange: () => void): () => void {
  window.addEventListener(SETTINGS_LAST_PATH_UPDATE_EVENT, onStoreChange);
  return () => window.removeEventListener(SETTINGS_LAST_PATH_UPDATE_EVENT, onStoreChange);
}

function getSidebarStoredHrefSnapshot(): string {
  return readPersistedSettingsPath() ?? DEFAULT_SETTINGS_HREF;
}

function getSidebarStoredHrefServerSnapshot(): string {
  return DEFAULT_SETTINGS_HREF;
}

/**
 * While on a settings route, the sidebar points at the current pathname.
 * After navigating elsewhere, the sidebar Settings link targets the last visited settings URL (sessionStorage).
 */
export function useSettingsSidebarHref(): string {
  const pathname = usePathname();
  const storedFallback = useSyncExternalStore(
    subscribe,
    getSidebarStoredHrefSnapshot,
    getSidebarStoredHrefServerSnapshot,
  );

  if (isUnderSettings(pathname ?? "")) {
    return pathname ?? DEFAULT_SETTINGS_HREF;
  }

  return storedFallback;
}
