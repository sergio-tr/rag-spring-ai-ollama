import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { integrationCredentials, productUrl } from "../fixtures/env";

test.describe("Projects API @api", () => {
  test("GET projects returns paged items", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const res = await request.get(productUrl("/projects?page=0&size=24"), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = (await res.json()) as { items?: unknown[]; totalElements?: number };
    expect(Array.isArray(body.items)).toBeTruthy();
  });

  test("POST create project returns 201 and id", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const name = `api-e2e-${Date.now()}-${test.info().workerIndex}`;
    const res = await request.post(productUrl("/projects"), {
      headers: { ...authHeaders(token), "Content-Type": "application/json" },
      data: { name },
    });
    expect(res.status(), await res.text()).toBe(201);
    const body = (await res.json()) as { id?: string; name?: string };
    expect(body.id).toBeTruthy();
    expect(body.name).toBe(name);
  });

  test("PUT activate project returns 200", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const listRes = await request.get(productUrl("/projects?page=0&size=24"), {
      headers: authHeaders(token),
    });
    expect(listRes.ok()).toBeTruthy();
    const list = (await listRes.json()) as { items: Array<{ id: string }> };
    expect(list.items.length).toBeGreaterThan(0);
    const projectId = list.items[0].id;
    const res = await request.put(productUrl(`/projects/${projectId}/activate`), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
  });
});
