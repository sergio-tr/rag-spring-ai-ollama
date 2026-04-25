import { expect, test } from "@playwright/test";
import { loginAsE2eAdmin } from "../support/helpers";

/**
 * E2E-09: ADMIN user can load admin health JSON and allowlist (seeded in Spring profile {@code e2e}).
 */
test.describe("Admin product API", () => {
  test("E2E-09 admin health and allowlist table @fullstack", async ({ page }) => {
    await loginAsE2eAdmin(page);
    await expect(page.getByRole("link", { name: /^admin$/i })).toBeVisible();
    await page.goto("/en/admin");
    await expect(page.getByRole("heading", { name: /administration|administración/i })).toBeVisible({
      timeout: 20_000,
    });

    await expect(page.getByText(/"status"/)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("table")).toBeVisible({ timeout: 15_000 });
  });
});
