"use client";

import { useLocale } from "next-intl";
import { useEffect } from "react";

/** Keeps <html lang> aligned with next-intl locale (root layout default is neutral). */
export function DocumentLang() {
  const locale = useLocale();

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  return null;
}
