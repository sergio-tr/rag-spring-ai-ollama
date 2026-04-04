/**
 * Single source for next-intl / use-intl time zone (avoids ENVIRONMENT_FALLBACK in production).
 * See https://next-intl.dev/docs/configuration#time-zone
 */
export function getAppTimeZone(): string {
  const v = process.env.NEXT_PUBLIC_TIMEZONE?.trim();
  return v && v.length > 0 ? v : "UTC";
}
