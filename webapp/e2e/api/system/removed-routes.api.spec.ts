import { expect, test } from "@playwright/test";
import { apiBaseUrl, productUrl } from "../fixtures/env";

/**
 * Guard: removed Lab evaluation HTTP paths and unprefixed backend auth/admin mirrors must not return 200.
 */
test.describe("Removed routes API @api @guard", () => {
  test("removed /lab/evaluations/* paths are not served", async ({ request }) => {
    for (const path of ["/lab/evaluations/llm", "/lab/evaluations/rag", "/lab/evaluations/embedding"]) {
      const res = await request.get(productUrl(path), { headers: { Accept: "application/json" } });
      expect(res.status(), `${path} must not be available`).not.toBe(200);
      expect([404, 405], `${path} status`).toContain(res.status());
    }
  });

  test("unprefixed /api/auth and /api/admin mirrors on backend are not served", async ({ request }) => {
    const base = apiBaseUrl();
    const auth = await request.get(`${base}/api/auth/me`, { headers: { Accept: "application/json" } });
    expect(auth.status()).toBe(404);

    const admin = await request.get(`${base}/api/admin/health`, { headers: { Accept: "application/json" } });
    expect(admin.status()).toBe(404);
  });
});
