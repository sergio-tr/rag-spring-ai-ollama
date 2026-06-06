import { expect, test } from "@playwright/test";

async function typeResetPasswords(page: import("@playwright/test").Page, password: string, repeat: string) {
  await expect(page.getByRole("heading", { name: /choose new password/i })).toBeVisible({ timeout: 15_000 });
  const passwordInput = page.locator("#password");
  const repeatPasswordInput = page.locator("#confirmPassword");
  await expect(passwordInput).toBeVisible();
  await expect(repeatPasswordInput).toBeVisible();
  await passwordInput.click();
  await passwordInput.fill(password);
  await repeatPasswordInput.click();
  await repeatPasswordInput.fill(repeat);
  await expect(passwordInput).toHaveValue(password, { timeout: 10_000 });
  await expect(repeatPasswordInput).toHaveValue(repeat, { timeout: 10_000 });
}

test.describe("Forgot/reset public flows", () => {
  test("forgot-password submits and shows neutral success @smoke", async ({ page }) => {
    await page.route("**/auth/forgot-password**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({}),
      });
    });

    await page.goto("/en/forgot-password", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByRole("heading", { name: /^Reset password$/i })).toBeVisible();
    const form = page.locator("form").first();
    const emailInput = form.getByLabel(/^Email$/i);
    await expect(emailInput).toBeVisible();
    await expect(emailInput).toBeEnabled();
    await emailInput.fill("user@example.com");
    await expect(emailInput).toHaveValue("user@example.com");
    await form.getByRole("button", { name: /send reset link/i }).click();
    await expect(page.getByRole("status")).toContainText(/if an account exists/i, { timeout: 10_000 });
  });

  test("reset-password validates repeat-password mismatch client-side @smoke", async ({ page }) => {
    let resetCalled = false;
    await page.route("**/auth/reset-password**", async (route) => {
      resetCalled = true;
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });

    await page.goto("/en/reset-password?token=test-token-1", {
      waitUntil: "domcontentloaded",
      timeout: 60_000,
    });
    await expect(page.getByRole("heading", { name: /^Choose new password$/i })).toBeVisible();
    const form = page.locator("form").first();
    await typeResetPasswords(page, "Password123!", "Different123!");
    await form.getByRole("button", { name: /set new password/i }).click();
    await expect(page.getByText(/passwords do not match/i)).toBeVisible({ timeout: 10_000 });
    expect(resetCalled).toBe(false);
  });

  test("reset-password shows invalid token errors when backend returns code @smoke", async ({ page }) => {
    await page.route("**/auth/reset-password**", async (route, request) => {
      const payload = request.postDataJSON() as { token?: string };
      expect(payload?.token).toBe("invalid-token");
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({ code: "RESET_TOKEN_INVALID" }),
      });
    });

    await page.goto("/en/reset-password?token=invalid-token", {
      waitUntil: "domcontentloaded",
      timeout: 60_000,
    });
    const form = page.locator("form").first();
    await typeResetPasswords(page, "Password123!", "Password123!");
    await form.getByRole("button", { name: /set new password/i }).click();
    await expect(page.getByText(/reset link is invalid/i)).toBeVisible({ timeout: 10_000 });
  });

  test("reset-password shows reused token errors when backend returns code @smoke", async ({ page }) => {
    await page.route("**/auth/reset-password**", async (route, request) => {
      const payload = request.postDataJSON() as { token?: string };
      expect(payload?.token).toBe("reused-token");
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({ code: "RESET_TOKEN_ALREADY_USED" }),
      });
    });

    await page.goto("/en/reset-password?token=reused-token", {
      waitUntil: "domcontentloaded",
      timeout: 60_000,
    });
    await expect(page).toHaveURL(/token=reused-token/);
    await expect(page.getByRole("heading", { name: /choose new password/i })).toBeVisible();
    const formAgain = page.locator("form").first();
    await typeResetPasswords(page, "Password123!", "Password123!");
    await formAgain.getByRole("button", { name: /set new password/i }).click();
    await expect(page.getByText(/already used/i)).toBeVisible({ timeout: 10_000 });
  });
});
