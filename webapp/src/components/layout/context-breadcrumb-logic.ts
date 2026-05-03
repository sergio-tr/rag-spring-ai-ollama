/**
 * Pure helpers for context breadcrumb / route section labels (no React).
 */

export type MainAppSection =
  | "projects"
  | "documents"
  | "chat"
  | "lab"
  | "admin"
  | "settings"
  | "unknown";

/** Match longest settings subpath first so /settings/account wins over /settings. */
const SETTINGS_TAB_PREFIXES = [
  ["/settings/account", "tabAccount"],
  ["/settings/data", "tabData"],
  ["/settings/presets", "tabPresets"],
  ["/settings/project", "tabProject"],
  ["/settings/user", "tabUser"],
] as const;

export type SettingsTabTranslationKey = (typeof SETTINGS_TAB_PREFIXES)[number][1] | "tabGeneral";

/**
 * next-intl pathname is locale-stripped (e.g. /chat, /settings/user).
 */
export function inferMainSection(pathname: string): MainAppSection {
  const p = pathname.replace(/\/$/, "") || "/";
  if (p === "/projects" || p.startsWith("/projects/")) return "projects";
  if (p === "/documents" || p.startsWith("/documents/")) return "documents";
  if (p === "/chat" || p.startsWith("/chat/")) return "chat";
  if (p === "/lab" || p.startsWith("/lab/")) return "lab";
  if (p === "/admin" || p.startsWith("/admin/")) return "admin";
  if (p.startsWith("/settings")) return "settings";
  return "unknown";
}

export function settingsTabKeyFromPath(pathname: string): SettingsTabTranslationKey | null {
  const p = pathname.replace(/\/$/, "") || "/";
  if (!p.startsWith("/settings")) return null;
  for (const [prefix, key] of SETTINGS_TAB_PREFIXES) {
    if (p === prefix || p.startsWith(`${prefix}/`)) return key;
  }
  return "tabGeneral";
}
