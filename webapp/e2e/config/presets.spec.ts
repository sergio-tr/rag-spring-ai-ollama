import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * E2E-06b: settings presets shell (merged from legacy product-smoke).
 */
test.describe("Settings presets", () => {
  test("E2E-06b settings presets page renders @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/settings/presets");
    await expect(page.getByRole("heading", { name: /^settings|ajustes$/i })).toBeVisible();
    await expect(page.getByText(/^presets$/i)).toBeVisible();
  });
});
