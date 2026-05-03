/**
 * Access JWT stored for cross-origin API calls to Spring (httpOnly cookies are not sent to another origin).
 * Refresh updates this value via {@link setAccessToken} after auth refresh endpoint calls.
 */
const STORAGE_KEY = "rag_access_token";

export function getAccessToken(): string | null {
  if (globalThis.window === undefined) {
    return null;
  }
  try {
    return sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function setAccessToken(token: string | null): void {
  if (globalThis.window === undefined) {
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
