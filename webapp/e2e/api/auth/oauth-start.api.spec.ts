import { expect, test } from "@playwright/test";
import { apiBaseUrl } from "../fixtures/env";

test.describe("OAuth start API @api", () => {
  test("oauth start returns redirect to Google authorize endpoint @fullstack", async ({ request }) => {
    const res = await request.get(`${apiBaseUrl()}/api/v5/auth/oauth/google/start?locale=en`, {
      maxRedirects: 0,
    });
    expect([302, 303, 307, 308]).toContain(res.status());
    const location = res.headers()["location"] ?? "";
    test.skip(
      /\/(en|es)\/login(?:\?|$)/.test(location),
      "OAuth start is disabled in this environment (redirects to login instead of Google).",
    );
    expect(location).toContain("accounts.google.com");
    expect(location).toContain("client_id=");
    expect(location).toContain("redirect_uri=");
    expect(location).toContain("state=");
    expect(location).toContain("response_type=code");
    expect(location).toContain("scope=openid");
  });
});
