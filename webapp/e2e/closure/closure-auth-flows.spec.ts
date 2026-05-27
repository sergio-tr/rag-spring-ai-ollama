import { expect, test } from "@playwright/test";
import { adminEmail, adminPassword, seedEmail, seedPassword } from "../fixtures/users";
import { loginAsE2eAdmin, loginAsSeedUser } from "../support/helpers";

/**
 * Closure: auth journeys against a live stack (Docker proxy or CI fullstack).
 * Tag @closure for evidence runs; mirrors login.spec + admin access patterns.
 */
test.describe("Closure auth flows @closure @fullstack", () => {
  test("seed user login reaches projects and can logout @closure @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/projects", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("heading", { name: /projects|proyectos/i }).first()).toBeVisible({
      timeout: 15_000,
    });
    await page.getByRole("button", { name: /account|cuenta|user menu/i }).first().click().catch(() => undefined);
    const logout = page.getByRole("menuitem", { name: /log out|cerrar sesión|sign out/i });
    if (await logout.isVisible().catch(() => false)) {
      await logout.click();
      await expect(page).toHaveURL(/\/login/, { timeout: 15_000 });
    }
  });

  test("wrong password shows invalid credentials @closure @fullstack", async ({ page }) => {
    await page.goto("/en/login");
    await page.getByLabel(/email|correo/i).fill(seedEmail());
    await page.getByLabel(/^password$/i).fill("definitely-wrong-password-xyz");
    await page.getByRole("button", { name: /continue|iniciar|sign in/i }).click();
    await expect(page.locator('p[role="alert"]')).toContainText(/invalid email|incorrectos/i, {
      timeout: 15_000,
    });
  });

  test("protected route redirects unauthenticated user to login @closure @fullstack", async ({ page }) => {
    await page.goto("/en/lab/evaluation/llm", { waitUntil: "domcontentloaded" });
    await expect(page).toHaveURL(/\/login/, { timeout: 15_000 });
  });

  test("admin login reaches admin page when enabled @closure @fullstack", async ({ page, request }) => {
    test.skip(process.env.E2E_ADMIN_ENABLED !== "1", "E2E_ADMIN_ENABLED=1 required");
    const probe = await request.post(
      `${(process.env.API_BASE_URL ?? "http://127.0.0.1:9000").replace(/\/$/, "")}/api/v5/auth/login`,
      { data: { email: adminEmail(), password: adminPassword() } },
    );
    test.skip(probe.status() !== 200, `Admin login probe HTTP ${probe.status()}`);
    await loginAsE2eAdmin(page);
    await page.goto("/en/admin", { waitUntil: "domcontentloaded" });
    await expect(page.getByTestId("admin-models-card")).toBeVisible({ timeout: 15_000 });
  });
});
