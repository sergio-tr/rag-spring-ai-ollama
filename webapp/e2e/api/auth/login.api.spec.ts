import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { integrationCredentials, productUrl } from "../fixtures/env";

test.describe("Auth API @api", () => {
  test("POST login returns accessToken", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    expect(token.length).toBeGreaterThan(10);
  });

  test("POST login with wrong password fails", async ({ request }) => {
    const { email } = integrationCredentials();
    const res = await request.post("/api/auth/login", {
      data: { email, password: "definitely-wrong-password-xyz" },
      headers: { "Content-Type": "application/json" },
    });
    expect(res.ok()).toBe(false);
    expect([400, 401, 403]).toContain(res.status());
  });

  test("GET projects without token returns 401 or 403", async ({ request }) => {
    const res = await request.get(productUrl("/projects?page=0&size=24"));
    expect([401, 403]).toContain(res.status());
  });

  test("GET projects with token returns 200", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/projects?page=0&size=24"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
  });
});
