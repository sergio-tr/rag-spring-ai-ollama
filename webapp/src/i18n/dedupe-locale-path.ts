import type { AppLocale } from "./routing";
import { routing } from "./routing";

function isLocaleSegment(segment: string): segment is AppLocale {
  return (routing.locales as readonly string[]).includes(segment);
}

/**
 * Collapses consecutive duplicate locale segments (e.g. /en/en/login → /en/login).
 * Used by middleware so mistaken double prefixes still resolve to a valid route.
 */
export function dedupeRepeatedLocaleSegments(pathname: string): string {
  const segments = pathname.split("/").filter(Boolean);
  if (segments.length < 2) {
    return pathname || "/";
  }

  let out = [...segments];
  while (
    out.length >= 2 &&
    isLocaleSegment(out[0]!) &&
    out[0] === out[1]
  ) {
    out = [out[0]!, ...out.slice(2)];
  }

  if (out.length === 0) {
    return "/";
  }
  return `/${out.join("/")}`;
}
