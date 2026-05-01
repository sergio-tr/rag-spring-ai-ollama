import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * Fullstack: settings shell and preferences card (i18n keys EN).
 */
test.describe("Settings", () => {
  test("E2E-11 navigate to settings shows preferences @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.getByRole("link", { name: /settings|ajustes/i }).click();
    await expect(page).toHaveURL(/\/en\/settings/);
    await expect(
      page.getByRole("main").getByText(/Appearance|Apariencia/i).first(),
    ).toBeVisible({ timeout: 15_000 });
  });

  test("E2E-12 settings user config page loads @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/settings/user");
    await expect(
      page.getByRole("main").getByText(/User defaults|Valores por defecto/i).first(),
    ).toBeVisible({ timeout: 15_000 });
  });
});
