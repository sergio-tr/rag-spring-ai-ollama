import { expect, test } from "@playwright/test";
import { apiBaseUrl } from "../fixtures/env";

test.describe("API error contracts @api", () => {
  test("unknown /api route returns JSON 404", async ({ request }) => {
    const res = await request.get(`${apiBaseUrl()}/api/does-not-exist`, {
      headers: { Accept: "application/json" },
    });
    expect(res.status()).toBe(404);
    const ct = res.headers()["content-type"] ?? "";
    expect(ct).toContain("application/json");
    const body = (await res.json()) as { success?: boolean; error?: { code?: string } };
    expect(body.success).toBe(false);
    expect(body.error?.code).toBeTruthy();
  });
});

