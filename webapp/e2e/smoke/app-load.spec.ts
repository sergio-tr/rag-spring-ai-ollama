import { expect, test } from "@playwright/test";

/**
 * Minimal PR smoke: HTTP 200 on locale entry (no Spring API required).
 */
test.describe("App load", () => {
  test("English locale route responds @smoke", async ({ page }) => {
    const res = await page.goto("/en");
    expect(res?.ok()).toBeTruthy();
  });
});
