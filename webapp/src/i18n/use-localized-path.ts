"use client";

import { useLocale } from "next-intl";
import { useCallback } from "react";
import { localizedPath } from "./localized-path";
import type { AppLocale } from "./routing";

/** Returns a stable callback that prefixes internal paths with the active UI locale. */
export function useLocalizedPath() {
  const locale = useLocale() as AppLocale;
  return useCallback((href: string) => localizedPath(href, locale), [locale]);
}
