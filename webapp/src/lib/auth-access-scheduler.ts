import { tryRefreshAccessToken } from "@/lib/api-client";

let scheduledTimer: ReturnType<typeof globalThis.setTimeout> | null = null;

function decodeJwtExpSeconds(jwt: string): number | null {
  try {
    const parts = jwt.split(".");
    if (parts.length < 2) return null;
    let payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const pad = payload.length % 4;
    if (pad) payload += "=".repeat(4 - pad);
    const json = JSON.parse(
      globalThis.atob(payload),
    ) as {
      exp?: number;
    };
    return typeof json.exp === "number" ? json.exp : null;
  } catch {
    return null;
  }
}

/** Schedules a silent refresh shortly before access JWT expiry (requires HS256-style JWT with {@code exp}). */
export function scheduleAccessTokenRefreshFromJwt(accessToken: string | null | undefined): void {
  clearScheduledAccessTokenRefresh();
  const trimmed = accessToken?.trim();
  if (!trimmed) return;

  const expSec = decodeJwtExpSeconds(trimmed);
  if (expSec == null) return;

  const skewMs = 120_000;
  const delay = expSec * 1000 - Date.now() - skewMs;

  if (delay < 15_000) {
    void tryRefreshAccessToken();
    return;
  }

  scheduledTimer = globalThis.setTimeout(() => {
    scheduledTimer = null;
    void tryRefreshAccessToken();
  }, delay);
}

export function clearScheduledAccessTokenRefresh(): void {
  if (scheduledTimer != null) {
    globalThis.clearTimeout(scheduledTimer);
    scheduledTimer = null;
  }
}
