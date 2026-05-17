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
    await expect(page.getByText(/could not load allowlist|no se pudo cargar/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: /check/i })).toBeDisabled();
    await expect(page.getByRole("button", { name: /add entry|añadir/i })).toBeDisabled();
  });
});
