import type { AppLocale } from "@/i18n/routing";
import { localizedPath } from "@/i18n/localized-path";

/** Full-page navigation - remounts AppProviders and guarantees a fresh QueryClient. */
export function hardNavigate(path: string, locale: string): void {
  if (globalThis.window === undefined) return;
  globalThis.window.location.assign(localizedPath(path, locale as AppLocale));
}
