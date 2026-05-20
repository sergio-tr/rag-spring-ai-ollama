import { expect, test } from "@playwright/test";
import { apiBaseUrl } from "../api/fixtures/env";

/**
 * Playwright-side duplicate of {@code scripts/e2e-stack-preflight.mjs} for local debugging.
 * CI chains the Node script before {@code test:e2e:preflight} to abort before large suites.
 */
test.describe("Stack health @preflight @stack-health", () => {
  test("backend actuator and web login respond within bounded time", async ({ page, request }) => {
    const backendHealth = await request.get(`${apiBaseUrl()}/actuator/health`, { timeout: 12_000 });
    expect(backendHealth.status(), await backendHealth.text()).toBe(200);

    const webResponse = await page.goto("/en/login", { waitUntil: "domcontentloaded", timeout: 12_000 });
    expect(webResponse?.ok(), "web login page should be reachable").toBeTruthy();
  });
});
