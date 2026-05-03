const TAB_QUERY_TO_PATH: Readonly<Record<string, string>> = {
  general: "/settings",
  user: "/settings/user",
  project: "/settings/project",
  presets: "/settings/presets",
  data: "/settings/data",
  account: "/settings/account",
};

/** Tab ids accepted in `?tab=` on `/settings` only (matched case-insensitively). */
export const SETTINGS_TAB_QUERY_IDS = Object.freeze([
  "general",
  "user",
  "project",
  "presets",
  "data",
  "account",
] as const);

/**
 * Maps `tab` search param (when present on `/settings`) to the canonical locale-neutral pathname.
 * Unknown or empty values resolve to `/settings` so the URL can be normalized without crashing.
 */
export function resolveSettingsPathFromTabQuery(tabFromSearchParams: string | null): string {
  if (tabFromSearchParams === null) return "/settings";
  const key = tabFromSearchParams.trim().toLowerCase();
  if (!key) return "/settings";
  return TAB_QUERY_TO_PATH[key] ?? "/settings";
}
