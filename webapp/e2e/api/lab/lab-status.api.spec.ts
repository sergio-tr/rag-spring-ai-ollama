import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { parseJsonExpectNonHtml } from "../fixtures/json-contract";
import { integrationCredentials, productUrl } from "../fixtures/env";

test.describe("Lab status API @api", () => {
  test("GET lab/status returns JSON capability map @api", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/lab/status"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const raw = await res.text();
    const body = parseJsonExpectNonHtml(raw, "GET lab/status") as {
      datasets?: { enabled?: boolean; questionCount?: number };
      evaluations?: Record<string, unknown>;
      classifier?: Record<string, unknown>;
      message?: string;
    };
    expect(body.datasets).toBeTruthy();
    expect(typeof body.datasets?.enabled).toBe("boolean");
    expect(typeof body.datasets?.questionCount).toBe("number");
    expect(body.evaluations).toBeTruthy();
    expect(body.classifier).toBeTruthy();
    expect(typeof body.message).toBe("string");
  });
});
