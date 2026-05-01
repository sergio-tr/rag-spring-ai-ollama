import { expect, test } from "@playwright/test";

function proxyBaseUrl(): string {
  const explicit = process.env.PLAYWRIGHT_NGINX_BASE_URL ?? process.env.NGINX_BASE_URL;
  if (explicit) return explicit.replace(/\/$/, "");
  return "https://127.0.0.1:8443";
}

test.describe("Nginx auth routing @api @fullstack", () => {
  test("oauth start is served by backend and redirects to Google", async ({ request }) => {
    let res;
    try {
      res = await request.get(`${proxyBaseUrl()}/api/v5/auth/oauth/google/start?locale=en`, {
        maxRedirects: 0,
        failOnStatusCode: false,
      });
    } catch {
      test.skip(true, "Reverse-proxy stack not reachable; run with nginx + backend + webapp up.");
      return;
    }
    test.skip([404, 502, 503].includes(res.status()), "Reverse-proxy stack reachable but unhealthy.");
    expect([302, 303, 307, 308]).toContain(res.status());
    expect(res.headers()["location"] ?? "").toContain("accounts.google.com");
  });

  test("session route is served by webapp BFF contract", async ({ request }) => {
    let res;
    try {
      res = await request.post(`${proxyBaseUrl()}/api/v5/auth/session`, {
        failOnStatusCode: false,
        data: {},
      });
    } catch {
      test.skip(true, "Reverse-proxy stack not reachable; run with nginx + backend + webapp up.");
      return;
    }
    test.skip([404, 502, 503].includes(res.status()), "Reverse-proxy stack reachable but unhealthy.");
    expect(res.status()).toBe(400);
  });
});
