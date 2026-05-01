import { expect, test } from "@playwright/test";

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
    await page.locator("#email").fill("user@example.test");
    await page.getByRole("button", { name: /send reset link/i }).click();
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
    await page.locator("#password").fill("Password123!");
    await page.locator("#confirmPassword").fill("Different123!");
    await page.getByRole("button", { name: /set new password/i }).click();
    await expect(page.getByRole("alert")).toContainText(/passwords do not match/i, { timeout: 10_000 });
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
    await page.locator("#password").fill("Password123!");
    await page.locator("#confirmPassword").fill("Password123!");
    await page.getByRole("button", { name: /set new password/i }).click();
    await expect(page.getByRole("alert")).toContainText(/reset link is invalid/i, { timeout: 10_000 });

    await page.goto("/en/reset-password?token=reused-token", {
      waitUntil: "domcontentloaded",
      timeout: 60_000,
    });
    await page.locator("#password").fill("Password123!");
    await page.locator("#confirmPassword").fill("Password123!");
    await page.getByRole("button", { name: /set new password/i }).click();
    await expect(page.getByRole("alert")).toContainText(/already used/i, { timeout: 10_000 });
  });
});
