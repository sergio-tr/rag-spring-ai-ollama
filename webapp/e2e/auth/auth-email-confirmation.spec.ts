import { expect, test } from "@playwright/test";
import {
  fetchConfirmTokenFromOutbox,
  uniqueM2AuthEmail,
} from "../support/auth-confirmation";

const REGISTER_PASSWORD = "Password123!";

/**
 * M2 product auth lifecycle against live stack (Spring profile e2e).
 * Requires email confirmation + mail outbox enabled on backend.
 */
test.describe("Auth email confirmation lifecycle @fullstack @critical", () => {
  test("E2E-M2-01 register lands on pending without session @fullstack @critical", async ({
    page,
  }) => {
    const email = uniqueM2AuthEmail();
    let sessionPostCount = 0;
    await page.route("**/auth/session**", async (route) => {
      sessionPostCount += 1;
      await route.fulfill({ status: 500, body: "should-not-be-called" });
    });

    await page.goto("/en/register", { waitUntil: "domcontentloaded", timeout: 60_000 });
    const form = page.locator("form").first();
    await form.getByLabel(/^Display name$/i).fill("M2 E2E User");
    await form.getByLabel(/^Email$/i).fill(email);
    await form.getByLabel(/^password$/i).fill(REGISTER_PASSWORD);
    await form.getByLabel(/repeat password/i).fill(REGISTER_PASSWORD);
    await form.getByRole("checkbox", { name: /privacy policy/i }).check();
    await form.getByRole("checkbox", { name: /terms and conditions/i }).check();
    await form.getByRole("button", { name: /register/i }).click();

    await expect(page).toHaveURL(
      new RegExp(String.raw`/en/register/pending\?email=${encodeURIComponent(email)}`),
      { timeout: 20_000 },
    );
    expect(sessionPostCount).toBe(0);
    await expect(page).not.toHaveURL(/\/en\/projects/, { timeout: 3_000 });
    await expect(page.getByText(/confirmation link/i).first()).toBeVisible();
  });

  test("E2E-M2-02 login blocked before confirmation @fullstack @critical", async ({ page }) => {
    const email = uniqueM2AuthEmail();
    await page.goto("/en/register", { waitUntil: "domcontentloaded", timeout: 60_000 });
    const form = page.locator("form").first();
    await form.getByLabel(/^Display name$/i).fill("M2 Blocked");
    await form.getByLabel(/^Email$/i).fill(email);
    await form.getByLabel(/^password$/i).fill(REGISTER_PASSWORD);
    await form.getByLabel(/repeat password/i).fill(REGISTER_PASSWORD);
    await form.getByRole("checkbox", { name: /privacy policy/i }).check();
    await form.getByRole("checkbox", { name: /terms and conditions/i }).check();
    await form.getByRole("button", { name: /register/i }).click();
    await expect(page).toHaveURL(/\/register\/pending/, { timeout: 20_000 });

    await page.goto("/en/login", { waitUntil: "domcontentloaded" });
    await page.getByLabel(/email|correo/i).fill(email);
    await page.getByLabel(/^password$/i).fill(REGISTER_PASSWORD);
    await page.getByRole("button", { name: /continue|sign in|iniciar/i }).click();
    await expect(page.locator('p[role="alert"]')).toContainText(/verification required|verificación/i, {
      timeout: 15_000,
    });
    await expect(page).not.toHaveURL(/\/projects/, { timeout: 3_000 });
  });

  test("E2E-M2-03 confirm email via outbox token @fullstack @critical", async ({
    page,
    request,
  }) => {
    const email = uniqueM2AuthEmail();
    await page.goto("/en/register", { waitUntil: "domcontentloaded", timeout: 60_000 });
    const form = page.locator("form").first();
    await form.getByLabel(/^Display name$/i).fill("M2 Confirm");
    await form.getByLabel(/^Email$/i).fill(email);
    await form.getByLabel(/^password$/i).fill(REGISTER_PASSWORD);
    await form.getByLabel(/repeat password/i).fill(REGISTER_PASSWORD);
    await form.getByRole("checkbox", { name: /privacy policy/i }).check();
    await form.getByRole("checkbox", { name: /terms and conditions/i }).check();
    await form.getByRole("button", { name: /register/i }).click();
    await expect(page).toHaveURL(/\/register\/pending/, { timeout: 20_000 });

    const token = await fetchConfirmTokenFromOutbox(request, email);
    await page.goto(`/en/confirm-email?token=${encodeURIComponent(token)}`, {
      waitUntil: "domcontentloaded",
    });
    await expect(page.getByText(/confirmed|confirmado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  test("E2E-M2-04 login succeeds after confirmation @fullstack @critical", async ({
    page,
    request,
  }) => {
    const email = uniqueM2AuthEmail();
    await page.goto("/en/register", { waitUntil: "domcontentloaded", timeout: 60_000 });
    const form = page.locator("form").first();
    await form.getByLabel(/^Display name$/i).fill("M2 Login After");
    await form.getByLabel(/^Email$/i).fill(email);
    await form.getByLabel(/^password$/i).fill(REGISTER_PASSWORD);
    await form.getByLabel(/repeat password/i).fill(REGISTER_PASSWORD);
    await form.getByRole("checkbox", { name: /privacy policy/i }).check();
    await form.getByRole("checkbox", { name: /terms and conditions/i }).check();
    await form.getByRole("button", { name: /register/i }).click();
    await expect(page).toHaveURL(/\/register\/pending/, { timeout: 20_000 });

    const token = await fetchConfirmTokenFromOutbox(request, email);
    await page.goto(`/en/confirm-email?token=${encodeURIComponent(token)}`, {
      waitUntil: "domcontentloaded",
    });
    await expect(page.getByText(/confirmed|confirmado/i).first()).toBeVisible({ timeout: 20_000 });

    await page.goto("/en/login", { waitUntil: "domcontentloaded" });
    await page.getByLabel(/email|correo/i).fill(email);
    await page.getByLabel(/^password$/i).fill(REGISTER_PASSWORD);
    await page.getByRole("button", { name: /continue|sign in|iniciar/i }).click();
    await expect(page).toHaveURL(/\/projects/, { timeout: 20_000 });
    await expect(page.getByRole("heading", { name: /projects|proyectos/i }).first()).toBeVisible({
      timeout: 15_000,
    });
  });
});
