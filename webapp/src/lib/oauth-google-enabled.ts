/**
 * Whether the Google OAuth CTA should be shown.
 *
 * Call from Server Components only - `NEXT_PUBLIC_*` is read at request time on the server,
 * so Docker/runtime env works without rebaking client bundles for this flag.
 */
export function isOAuthGoogleEnabled(): boolean {
  return process.env.NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED === "true";
}
