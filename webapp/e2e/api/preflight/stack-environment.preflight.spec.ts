import { expect, test } from "@playwright/test";
import { skipIfBlockedEnvironment } from "../fixtures/blocked-environment";
import { runApiStackPreflight } from "../fixtures/stack-preflight";
import { apiBaseUrl, productBasePath } from "../fixtures/env";

test.describe("API stack environment preflight @preflight @api", () => {
  test("frontend, backend health, seed login, selectable models, and seed project fixture", async ({
    request,
  }) => {
    test.setTimeout(Number.parseInt(process.env.PLAYWRIGHT_API_TEST_TIMEOUT_MS ?? "120000", 10));
    try {
      await runApiStackPreflight(request);
    } catch (e) {
      skipIfBlockedEnvironment(e);
    }
    expect(apiBaseUrl(), "resolved product API origin").toMatch(/^https?:\/\//);
    expect(productBasePath()).toBe("/api/v5");
  });
});
