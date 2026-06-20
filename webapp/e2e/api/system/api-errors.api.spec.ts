import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { apiBaseUrl, integrationCredentials, productUrl } from "../fixtures/env";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";

test.describe("API error contracts @api", () => {
  test("authenticated unknown product API route returns JSON 404", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/does-not-exist"), {
      headers: { ...authHeaders(token), Accept: "application/json" },
    });
    expect(res.status()).toBe(404);
    const ct = res.headers()["content-type"] ?? "";
    expect(ct).toContain("application/json");
    const body = (await res.json()) as { success?: boolean; error?: { code?: string } };
    expect(body.success).toBe(false);
    expect(body.error?.code).toBeTruthy();
  });

  test("authenticated unknown product route returns JSON 404 not HTML @api", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/this-route-should-not-exist-12345"), {
      headers: { ...authHeaders(token), Accept: "application/json" },
    });
    expect(res.status()).toBe(404);
    const ct = res.headers()["content-type"] ?? "";
    expect(ct).toContain("application/json");
    const raw = await res.text();
    const body = parseJsonExpectNonHtml(raw, "unknown product route") as {
      success?: boolean;
      error?: { code?: string };
    };
    expect(body.success).toBe(false);
    expect(body.error?.code).toBeTruthy();
  });
});

