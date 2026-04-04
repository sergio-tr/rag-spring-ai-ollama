import { expect, test } from "@playwright/test";
import { authHeaders, loginAndGetToken } from "../fixtures/auth";
import { integrationCredentials, productUrl } from "../fixtures/env";

test.describe("Chat / conversations API @api", () => {
  test("GET conversations for first project", async ({ request }) => {
    const { email, password } = integrationCredentials();
    const token = await loginAndGetToken(request, email, password);
    const listRes = await request.get(productUrl("/projects?page=0&size=24"), {
      headers: authHeaders(token),
    });
    expect(listRes.ok()).toBeTruthy();
    const list = (await listRes.json()) as { items: Array<{ id: string }> };
    expect(list.items.length).toBeGreaterThan(0);
    const projectId = list.items[0].id;

    const res = await request.get(productUrl(`/projects/${projectId}/conversations`), {
      headers: authHeaders(token),
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = (await res.json()) as unknown;
    expect(Array.isArray(body)).toBeTruthy();
  });
});
