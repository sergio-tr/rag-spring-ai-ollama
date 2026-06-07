import { expect, test } from "@playwright/test";
import { gotoWithProxyRetry } from "../support/helpers";
import { actuatorHealthUrl } from "../api/fixtures/env";

/**
 * Playwright-side duplicate of {@code scripts/e2e-stack-preflight.mjs} for local debugging.
 * CI chains the Node script before {@code test:e2e:preflight} to abort before large suites.
 */
test.describe("Stack health @preflight @stack-health", () => {
  test("backend actuator and web login respond within bounded time", async ({ page, request }) => {
    const backendLiveness = await request.get(actuatorHealthUrl("/liveness"), { timeout: 12_000 });
    expect(backendLiveness.status(), await backendLiveness.text()).toBe(200);

    await gotoWithProxyRetry(page, "/en/login");
    expect(page.url(), "web login page should be reachable").toMatch(/\/en\/login/);
  });
});
