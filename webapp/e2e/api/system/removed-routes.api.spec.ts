import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { apiBaseUrl, integrationCredentials, productUrl } from "../fixtures/env";

/**
 * Guard: removed Lab evaluation HTTP paths and unprefixed backend auth/admin mirrors must not return 200.
 */
test.describe("Removed routes API @api @guard", () => {
  test("removed /lab/evaluations/* paths are not served", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const headers = authHeaders(token);

    // Product API is authenticated; unauthenticated GET would 401 even for unknown paths.
    for (const path of ["/lab/evaluations/llm", "/lab/evaluations/rag", "/lab/evaluations/embedding"]) {
      const res = await request.get(productUrl(path), { headers });
      expect(res.status(), `${path} must not be available`).not.toBe(200);
      expect([404, 405], `${path} status`).toContain(res.status());
    }
  });

  test("unprefixed /api/auth and /api/admin mirrors on backend are not served", async ({ request }) => {
    const base = apiBaseUrl();
    const headers = { Accept: "application/json" };

    const authMe = await request.get(`${base}/api/auth/me`, { headers });
    expect(authMe.status()).toBe(404);

    const authLogin = await request.post(`${base}/api/auth/login`, {
      headers: { ...headers, "Content-Type": "application/json" },
      data: { email: "nobody@example.com", password: "x" },
    });
    expect(authLogin.status()).toBe(404);

    const adminHealth = await request.get(`${base}/api/admin/health`, { headers });
    expect(adminHealth.status()).toBe(404);

    const adminPull = await request.post(`${base}/api/admin/models/pull`, {
      headers: { ...headers, "Content-Type": "application/json" },
      data: { model: "llama3" },
    });
    expect(adminPull.status()).toBe(404);
  });
});
