import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { integrationCredentials, productUrl } from "../fixtures/env";

test.describe("Config and presets API @api", () => {
  test("GET config/schema returns version + fields array @api", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/config/schema"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = parseJsonExpectNonHtml(await res.text(), "GET config/schema") as {
      version?: number;
      fields?: unknown[];
    };
    expect(typeof body.version).toBe("number");
    expect(Array.isArray(body.fields)).toBe(true);
  });

  test("GET config/user returns JSON object @api", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/config/user"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = parseJsonExpectNonHtml(await res.text(), "GET config/user") as Record<string, unknown>;
    expect(body && typeof body === "object").toBe(true);
  });

  test("GET presets returns JSON array @api", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/presets"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = parseJsonExpectNonHtml(await res.text(), "GET presets") as unknown;
    expect(Array.isArray(body)).toBe(true);
  });
});
