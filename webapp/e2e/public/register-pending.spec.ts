import { expect, test } from "@playwright/test";

function uniqueEmail(): string {
  const stamp = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  return `pending-${stamp}@example.com`;
}

test.describe("Register pending flow", () => {
  test("register lands on pending and does not call session endpoint @smoke", async ({ page }) => {
    const email = uniqueEmail();
    let sessionPostCount = 0;

    await page.route("**/auth/register**", async (route) => {
      await route.fulfill({
        status: 202,
        contentType: "application/json",
        body: JSON.stringify({
          status: "PENDING_EMAIL_VERIFICATION",
          login: null,
          confirmationDelivery: "outbox-only",
        }),
      });
    });
    await page.route("**/auth/session**", async (route) => {
      sessionPostCount += 1;
      await route.fulfill({ status: 500, body: "should-not-be-called" });
    });

    await page.goto("/en/register", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByRole("heading", { name: /^Create account$/i })).toBeVisible();
    const form = page.locator("form").first();
    const nameInput = form.getByLabel(/^Display name$/i);
    const emailInput = form.getByLabel(/^Email$/i);
    await expect(nameInput).toBeVisible();
    await expect(emailInput).toBeVisible();
    await nameInput.fill("Pending User");
    await emailInput.fill(email);
    await expect(nameInput).toHaveValue("Pending User");
    await expect(emailInput).toHaveValue(email);
    await form.getByLabel(/^password$/i).fill("Password123!");
    await form.getByLabel(/repeat password/i).fill("Password123!");
    await form.getByRole("checkbox", { name: /privacy policy/i }).check();
    await form.getByRole("checkbox", { name: /terms and conditions/i }).check();
    await form.getByRole("button", { name: /register/i }).click();

    await expect(page).toHaveURL(new RegExp(String.raw`/en/register/pending\?email=${encodeURIComponent(email)}`), {
      timeout: 15_000,
    });
    expect(sessionPostCount).toBe(0);
    await expect(page).not.toHaveURL(/\/en\/projects/, { timeout: 3_000 });
  });
});
