import { expect, test } from "@playwright/test";

async function typeResetPasswords(page: import("@playwright/test").Page, password: string, repeat: string) {
  const passwordInput = page.locator("#password");
  const repeatPasswordInput = page.locator("#confirmPassword");
  await expect(passwordInput).toBeVisible();
  await expect(repeatPasswordInput).toBeVisible();
  await passwordInput.click();
  await passwordInput.type(password);
  await repeatPasswordInput.click();
  await repeatPasswordInput.type(repeat);
  await expect(passwordInput).toHaveValue(password);
  await expect(repeatPasswordInput).toHaveValue(repeat);
}

test.describe("Forgot/reset public flows", () => {
  test("forgot-password submits and shows neutral success @smoke", async ({ page }) => {
    await page.route("**/api/v5/auth/forgot-password", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({}),
      });
    });

    await page.goto("/en/forgot-password", { waitUntil: "domcontentloaded", timeout: 60_000 });
    const form = page.locator("form").first();
    const emailInput = form.locator("#email");
    await expect(emailInput).toBeVisible();
    await expect(emailInput).toBeEnabled();
    await emailInput.click();
    await emailInput.type("user@example.com");
    await expect(emailInput).toHaveValue("user@example.com");
    await form.getByRole("button", { name: /send reset link/i }).click();
    await expect(page.getByRole("status")).toContainText(/if an account exists/i, { timeout: 10_000 });
  });

  test("reset-password validates repeat-password mismatch client-side @smoke", async ({ page }) => {
    let resetCalled = false;
    await page.route("**/api/v5/auth/reset-password", async (route) => {
      resetCalled = true;
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });

    await page.goto("/en/reset-password?token=test-token-1", {
      waitUntil: "domcontentloaded",
      timeout: 60_000,
    });
    const form = page.locator("form").first();
    await typeResetPasswords(page, "Password123!", "Different123!");
    await form.getByRole("button", { name: /set new password/i }).click();
    await expect(page.getByText(/passwords do not match/i)).toBeVisible({ timeout: 10_000 });
    expect(resetCalled).toBe(false);
  });

  test("reset-password shows invalid/reused token errors when backend returns codes @smoke", async ({ page }) => {
    await page.route("**/api/v5/auth/reset-password", async (route, request) => {
      const payload = request.postDataJSON() as { token?: string };
      const code = payload?.token === "invalid-token" ? "RESET_TOKEN_INVALID" : "RESET_TOKEN_ALREADY_USED";
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({ code }),
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

    await page.goto("/en/reset-password?token=reused-token", {
      waitUntil: "domcontentloaded",
      timeout: 60_000,
    });
    const formAgain = page.locator("form").first();
    await typeResetPasswords(page, "Password123!", "Password123!");
    await formAgain.getByRole("button", { name: /set new password/i }).click();
    await expect(page.getByText(/already used/i)).toBeVisible({ timeout: 10_000 });
  });
});
