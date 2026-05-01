import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { integrationCredentials, productUrl } from "../fixtures/env";

test.describe("Auth session /me API @api", () => {
  test("GET auth/me with Bearer returns JSON Me shape @api", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/auth/me"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const raw = await res.text();
    const body = parseJsonExpectNonHtml(raw, "GET auth/me") as {
      userId?: string;
      email?: string;
      roleName?: string;
    };
    expect(body.userId).toBeTruthy();
    expect(body.email).toBeTruthy();
    expect(body.roleName).toBeTruthy();
  });

  test("GET auth/me without Authorization returns JSON error, not HTML @api", async ({ request }) => {
    const res = await request.get(productUrl("/auth/me"), {
      headers: { Accept: "application/json" },
    });
    expect([401, 403]).toContain(res.status());
    const raw = await res.text();
    parseJsonExpectNonHtml(raw, "GET auth/me unauthenticated");
  });
});
