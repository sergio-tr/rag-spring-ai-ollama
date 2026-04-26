import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * E2E-09: seed USER must not access admin product API (forbidden UX).
 */
test.describe("Admin access for seed user", () => {
  test("E2E-09 admin API forbidden for seed USER @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await expect(page.getByRole("link", { name: /^admin$/i })).toHaveCount(0);
    await page.goto("/en/admin");
    await expect(page.getByRole("heading", { name: /administration|administración/i })).toBeVisible();
    await expect(page.locator('p[role="alert"]')).toContainText(/forbidden|prohibido/i, {
      timeout: 15_000,
    });
  });
});
