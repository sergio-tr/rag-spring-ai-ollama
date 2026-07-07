import type { AppLocale } from "./routing";
import { routing } from "./routing";

const EXTERNAL_OR_SPECIAL_SCHEME = /^(https?:|mailto:|tel:|blob:|data:)/i;

function isLocaleSegment(segment: string): segment is AppLocale {
  return (routing.locales as readonly string[]).includes(segment);
}

function shouldSkipLocalePrefix(pathname: string): boolean {
  return pathname.startsWith("/api/") || pathname.startsWith("/_next/");
}

function splitHref(href: string): { pathname: string; search: string; hash: string } {
  let rest = href;
  let hash = "";
  const hashIdx = rest.indexOf("#");
  if (hashIdx >= 0) {
    hash = rest.slice(hashIdx);
    rest = rest.slice(0, hashIdx);
  }

  let search = "";
  const searchIdx = rest.indexOf("?");
  if (searchIdx >= 0) {
    search = rest.slice(searchIdx);
    rest = rest.slice(0, searchIdx);
  }

  const pathname = rest.startsWith("/") ? rest : rest.length > 0 ? `/${rest}` : "/";
  return { pathname, search, hash };
}

/**
 * Prefixes an internal app path with the active locale.
 * Pass-through for external URLs, special schemes, anchor-only links, and non-localized API paths.
 */
export function localizedPath(href: string, locale: AppLocale): string {
  if (!href) {
    return href;
  }

  if (href.startsWith("#") || EXTERNAL_OR_SPECIAL_SCHEME.test(href)) {
    return href;
  }

  const { pathname, search, hash } = splitHref(href);

  if (shouldSkipLocalePrefix(pathname)) {
    return `${pathname}${search}${hash}`;
  }

  const segments = pathname.split("/").filter(Boolean);
  if (segments.length > 0 && isLocaleSegment(segments[0]!)) {
    segments.shift();
  }

  const barePath = segments.length > 0 ? `/${segments.join("/")}` : "/";
  const localized =
    barePath === "/" ? `/${locale}` : `/${locale}${barePath}`;

  return `${localized}${search}${hash}`;
}
