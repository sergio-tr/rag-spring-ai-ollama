import { expect, test } from "@playwright/test";

/**
 * Smoke E2E: no backend required — validates routing and static render of auth shells.
 */
test.describe("Public auth pages", () => {
  test("login page shows title and form @smoke", async ({ page }) => {
    await page.goto("/en/login");
    await expect(page.locator("h1")).toBeVisible();
    await expect(page.getByLabel(/email|correo/i)).toBeVisible();
  });

  test("register page shows title @smoke", async ({ page }) => {
    await page.goto("/en/register");
    await expect(page.locator("h1")).toBeVisible();
  });
});
