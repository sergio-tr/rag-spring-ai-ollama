/**
 * Cookie name for session access token (httpOnly, set via BFF routes after login).
 * Must match backend / BFF when integrating with Spring Security.
 */
export const AUTH_ACCESS_COOKIE_NAME =
  process.env.NEXT_PUBLIC_AUTH_ACCESS_COOKIE_NAME?.trim() || "rag_access_token";

export const AUTH_REFRESH_COOKIE_NAME =
  process.env.NEXT_PUBLIC_AUTH_REFRESH_COOKIE_NAME?.trim() || "rag_refresh_token";
