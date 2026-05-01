import { expect, test } from "@playwright/test";

function uniqueEmail(): string {
  const stamp = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  return `pending-${stamp}@example.com`;
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
    const form = page.locator("form").first();
    const nameInput = form.locator("#name");
    const emailInput = form.locator("#email");
    await expect(nameInput).toBeVisible();
    await expect(emailInput).toBeVisible();
    await nameInput.click();
    await nameInput.type("Pending User");
    await emailInput.click();
    await emailInput.type(email);
    await expect(nameInput).toHaveValue("Pending User");
    await expect(emailInput).toHaveValue(email);
    await form.getByLabel(/^password$/i).fill("Password123!");
    await form.getByLabel(/repeat password/i).fill("Password123!");
    await form.getByRole("checkbox", { name: /privacy policy/i }).check();
    await form.getByRole("checkbox", { name: /terms and conditions/i }).check();
    await form.getByRole("button", { name: /register/i }).click();

    await expect(page).toHaveURL(new RegExp(`/en/register/pending\\?email=${encodeURIComponent(email)}`), {
      timeout: 15_000,
    });
    expect(sessionPostCount).toBe(0);
    await expect(page).not.toHaveURL(/\/en\/projects/, { timeout: 3_000 });
  });
});
