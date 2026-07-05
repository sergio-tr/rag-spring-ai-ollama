import { describe, expect, it, vi, afterEach } from "vitest";
import { isOAuthGoogleEnabled } from "./oauth-google-enabled";

describe("isOAuthGoogleEnabled", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("returns true when NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED is true", () => {
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "true");
    expect(isOAuthGoogleEnabled()).toBe(true);
  });

  it("returns false when flag is unset or not true", () => {
    vi.stubEnv("NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED", "false");
    expect(isOAuthGoogleEnabled()).toBe(false);
  });
});
