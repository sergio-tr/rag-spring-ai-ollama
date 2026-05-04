import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { apiBaseUrl, integrationCredentials, productUrl } from "../fixtures/env";

/**
 * Serial HTTP smoke chain for operators. Tag `@system` allows `npm run test:api -- --grep @system`.
 */
test.describe.serial("System smoke chain @api @system", () => {
  let token: string;

  test("actuator health returns JSON with status", async ({ request }) => {
    const res = await request.get(`${apiBaseUrl()}/actuator/health`);
    expect(res.status(), await res.text()).toBe(200);
    const body = (await res.json()) as { status?: string };
    expect(body.status).toBeTruthy();
  });

  test("login + projects list (seed user)", async ({ request }) => {
    const { email, password } = integrationCredentials();
    token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/projects?page=0&size=24"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = (await res.json()) as { items?: unknown[] };
    expect(Array.isArray(body.items)).toBeTruthy();
    expect((body.items ?? []).length).toBeGreaterThanOrEqual(1);
  });

  test("GET config/user", async ({ request }) => {
    const res = await request.get(productUrl("/config/user"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
  });

  test("GET lab/status JSON", async ({ request }) => {
    const res = await request.get(productUrl("/lab/status"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = (await res.json()) as {
      datasets?: {
        enabled?: boolean;
        datasetKindsReady?: boolean;
        legacyQuestionCountDeprecated?: unknown;
      };
      datasetKindsReady?: boolean;
      referenceBundleAvailable?: boolean;
      referenceBundleValid?: boolean;
      countsByDatasetKind?: Record<string, unknown>;
      evaluations?: Record<string, unknown>;
      classifier?: Record<string, unknown>;
      message?: string;
    };
    expect(body.datasets).toEqual(
      expect.objectContaining({
        enabled: expect.any(Boolean),
        datasetKindsReady: expect.any(Boolean),
      }),
    );
    expect(typeof body.datasetKindsReady).toBe("boolean");
    expect(typeof body.referenceBundleAvailable).toBe("boolean");
    expect(typeof body.referenceBundleValid).toBe("boolean");
    expect(body.countsByDatasetKind).toEqual(expect.any(Object));
    expect(body.evaluations).toEqual(expect.any(Object));
    expect(body.classifier).toEqual(expect.any(Object));
    expect(typeof body.message).toBe("string");
  });

  test("classifier health (optional)", async ({ request }) => {
    const classifierUrl = (process.env.CLASSIFIER_URL ?? "").replace(/\/$/, "");
    test.skip(!classifierUrl, "Set CLASSIFIER_URL to enable classifier health check");
    const res = await request.get(`${classifierUrl}/health`);
    expect(res.status(), await res.text()).toBe(200);
  });

  test("OpenAPI JSON or 404", async ({ request }) => {
    const res = await request.get(`${apiBaseUrl()}/v3/api-docs`);
    const code = res.status();
    expect([200, 404]).toContain(code);
    if (code === 200) {
      await res.json();
    }
  });

  test("readiness 200 or 503", async ({ request }) => {
    const res = await request.get(`${apiBaseUrl()}/actuator/health/readiness`);
    expect([200, 503]).toContain(res.status());
  });

  // Legacy query smoke intentionally removed (product-only).
});
