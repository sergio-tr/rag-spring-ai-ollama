/**
 * Access JWT stored for cross-origin API calls to Spring (httpOnly cookies are not sent to another origin).
 * Refresh updates this value via {@link setAccessToken} after /api/auth/refresh.
 */
const STORAGE_KEY = "rag_access_token";

export function getAccessToken(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    return sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function setAccessToken(token: string | null): void {
  if (typeof window === "undefined") {
    return;
  }
  try {
    if (token) {
      sessionStorage.setItem(STORAGE_KEY, token);
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // ignore quota / private mode
  }
}
