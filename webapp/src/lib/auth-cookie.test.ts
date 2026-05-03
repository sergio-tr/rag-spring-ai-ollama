import { describe, expect, it } from "vitest";

import { AUTH_ACCESS_COOKIE_NAME, AUTH_REFRESH_COOKIE_NAME } from "./auth-cookie";

describe("auth-cookie", () => {
  it("exposes default cookie names", () => {
    expect(AUTH_ACCESS_COOKIE_NAME.length).toBeGreaterThan(0);
    expect(AUTH_REFRESH_COOKIE_NAME.length).toBeGreaterThan(0);
  });
});
