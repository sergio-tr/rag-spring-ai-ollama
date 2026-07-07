import { expect, test } from "@playwright/test";

/**
 * Client-side validation on register (no successful registration - avoids duplicate email in CI).
 */
test.describe("Register form validation", () => {
  test("empty submit shows validation alerts @smoke", async ({ page }) => {
    await page.goto("/en/register");
    await expect(page.getByRole("heading", { name: /^Create account$/i })).toBeVisible();
    await page.getByRole("button", { name: /register|registrarse/i }).click();
    await expect(page.getByRole("alert").first()).toBeVisible({ timeout: 10_000 });
  });

  test("invalid email shows field alert @smoke", async ({ page }) => {
    await page.goto("/en/register");
    await expect(page.getByRole("heading", { name: /^Create account$/i })).toBeVisible();
    const form = page.locator("form").first();
    const emailInput = form.getByLabel(/^Email$/i);
    await form.getByLabel(/^Display name$/i).fill("Test User");
    await emailInput.fill("not-an-email");
    await form.getByLabel(/^Password$/i).fill("short");
    await page.getByRole("button", { name: /register|registrarse/i }).click();
    await expect(emailInput.locator("..").getByRole("alert")).toBeVisible({ timeout: 10_000 });
  });
});
