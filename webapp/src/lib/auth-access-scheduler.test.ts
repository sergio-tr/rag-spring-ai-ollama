import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("@/lib/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api-client")>();
  return { ...actual, tryRefreshAccessToken: vi.fn().mockResolvedValue(undefined) };
});

import { tryRefreshAccessToken } from "@/lib/api-client";
import {
  clearScheduledAccessTokenRefresh,
  scheduleAccessTokenRefreshFromJwt,
} from "./auth-access-scheduler";

function jwtStubWithExp(expSeconds: number): string {
  const payload = globalThis.btoa(JSON.stringify({ exp: expSeconds }));
  return `header.${payload}.sig`;
}

describe("auth-access-scheduler", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-01T12:00:00.000Z"));
    clearScheduledAccessTokenRefresh();
    vi.mocked(tryRefreshAccessToken).mockClear();
  });

  afterEach(() => {
    clearScheduledAccessTokenRefresh();
    vi.useRealTimers();
  });

  it("schedules silent refresh shortly before JWT exp", async () => {
    const nowMs = Date.now();
    const expSec = Math.floor(nowMs / 1000) + 7200;
    scheduleAccessTokenRefreshFromJwt(jwtStubWithExp(expSec));
    expect(tryRefreshAccessToken).not.toHaveBeenCalled();

    const skewMs = 120_000;
    const delayMs = expSec * 1000 - nowMs - skewMs;
    await vi.advanceTimersByTimeAsync(delayMs - 1);
    expect(tryRefreshAccessToken).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(2);
    expect(tryRefreshAccessToken).toHaveBeenCalledTimes(1);
  });

  it("refreshes immediately when access token expires within the skew window", () => {
    const expSec = Math.floor(Date.now() / 1000) + 30;
    scheduleAccessTokenRefreshFromJwt(jwtStubWithExp(expSec));
    expect(tryRefreshAccessToken).toHaveBeenCalledTimes(1);
  });

  it("clears a pending scheduled refresh", async () => {
    const nowMs = Date.now();
    const expSec = Math.floor(nowMs / 1000) + 7200;
    scheduleAccessTokenRefreshFromJwt(jwtStubWithExp(expSec));
    clearScheduledAccessTokenRefresh();
    await vi.runAllTimersAsync();
    expect(tryRefreshAccessToken).not.toHaveBeenCalled();
  });

  it("no-ops for empty or missing access tokens", () => {
    scheduleAccessTokenRefreshFromJwt(null);
    scheduleAccessTokenRefreshFromJwt("   ");
    expect(tryRefreshAccessToken).not.toHaveBeenCalled();
  });

  it("no-ops when JWT cannot be decoded", () => {
    scheduleAccessTokenRefreshFromJwt("not-a-jwt");
    scheduleAccessTokenRefreshFromJwt("only.two.parts");
    expect(tryRefreshAccessToken).not.toHaveBeenCalled();
  });

  it("no-ops when JWT payload has no exp claim", () => {
    const payload = globalThis.btoa(JSON.stringify({ sub: "user" }));
    scheduleAccessTokenRefreshFromJwt(`h.${payload}.sig`);
    expect(tryRefreshAccessToken).not.toHaveBeenCalled();
  });
});
