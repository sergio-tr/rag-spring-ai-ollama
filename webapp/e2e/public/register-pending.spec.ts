import { expect, test } from "@playwright/test";

function uniqueEmail(): string {
  const stamp = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  return `pending-${stamp}@example.test`;
}

test.describe("Register pending flow", () => {
  test("register lands on pending and does not call session endpoint @smoke", async ({ page }) => {
    const email = uniqueEmail();
    let sessionPostCount = 0;

    await page.route("**/api/v5/auth/register", async (route) => {
      await route.fulfill({
        status: 202,
        contentType: "application/json",
        body: JSON.stringify({
          status: "PENDING_EMAIL_VERIFICATION",
          login: null,
        }),
      });
    });
    await page.route("**/api/v5/auth/session", async (route) => {
      sessionPostCount += 1;
      await route.fulfill({ status: 500, body: "should-not-be-called" });
    });

    await page.goto("/en/register", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await page.locator("#name").fill("Pending User");
    await page.locator("#email").fill(email);
    await page.locator("#password").fill("Password123!");
    await page.locator("#confirmPassword").fill("Password123!");
    await page.getByRole("checkbox", { name: /privacy policy/i }).check();
    await page.getByRole("checkbox", { name: /terms and conditions/i }).check();
    await page.getByRole("button", { name: /register/i }).click();

    await expect(page).toHaveURL(new RegExp(`/en/register/pending\\?email=${encodeURIComponent(email)}`), {
      timeout: 15_000,
    });
    expect(sessionPostCount).toBe(0);
    await expect(page).not.toHaveURL(/\/en\/projects/, { timeout: 3_000 });
  });
});
