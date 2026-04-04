import { setAccessToken } from "@/lib/access-token";

/**
 * Persists tokens into httpOnly cookies via Next.js route handlers (same origin).
 */
export async function commitSessionCookie(tokens: {
  accessToken: string;
  refreshToken?: string;
}): Promise<void> {
  const res = await fetch("/api/auth/session", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(tokens),
  });
  if (!res.ok) {
    throw new Error("session_cookie_failed");
  }
  setAccessToken(tokens.accessToken);
}

export async function clearSessionCookie(): Promise<void> {
  setAccessToken(null);
  await fetch("/api/auth/logout", {
    method: "POST",
    credentials: "same-origin",
  });
}
