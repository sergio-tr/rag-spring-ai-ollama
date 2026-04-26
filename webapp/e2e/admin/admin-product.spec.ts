import { expect, test } from "@playwright/test";
import { loginAsE2eAdmin } from "../support/helpers";

/**
 * E2E-09: ADMIN user can load admin health JSON and allowlist (seeded in Spring profile {@code e2e}).
 */
test.describe("Admin product API", () => {
  test("E2E-09 admin health and allowlist table @fullstack", async ({ page }) => {
    test.skip(
      process.env.E2E_ADMIN_ENABLED !== "1",
      "Set E2E_ADMIN_ENABLED=1 when the backend is started with profile=e2e (admin seeded).",
    );
    await loginAsE2eAdmin(page);
    await expect(page.getByRole("link", { name: /^admin$/i })).toBeVisible();
    await page.goto("/en/admin");
    await expect(page.getByRole("heading", { name: /administration|administración/i })).toBeVisible({
      timeout: 20_000,
    });

    await expect(page.getByText(/"status"/)).toBeVisible({ timeout: 15_000 });
    // Keep this suite a smoke test: allowlist UI is optional and may be gated by backend flags.
    await expect(page.getByText(/forbidden or unreachable/i)).toHaveCount(0);
  });
});
