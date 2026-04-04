import { expect, test } from "@playwright/test";

/**
 * Client-side validation on register (no successful registration — avoids duplicate email in CI).
 */
test.describe("Register form validation", () => {
  test("empty submit shows validation alerts @smoke", async ({ page }) => {
    await page.goto("/en/register");
    await page.getByRole("button", { name: /register|registrarse/i }).click();
    await expect(page.getByRole("alert").first()).toBeVisible({ timeout: 10_000 });
  });

  test("invalid email shows field alert @smoke", async ({ page }) => {
    await page.goto("/en/register");
    await page.locator("#name").fill("Test User");
    await page.locator("#email").fill("not-an-email");
    await page.locator("#password").fill("short");
    await page.getByRole("button", { name: /register|registrarse/i }).click();
    await expect(page.locator("#email").locator("..").getByRole("alert")).toBeVisible({ timeout: 10_000 });
  });
});
