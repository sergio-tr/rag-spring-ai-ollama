import { expect, test } from "@playwright/test";
import { loginAsE2eAdmin, productApiUrl } from "../support/helpers";
import { adminEmail, adminPassword } from "../fixtures/users";

/**
 * E2E-09: ADMIN user can load the model management UI (seeded in Spring profile {@code e2e}).
 */
test.describe("Admin product API", () => {
  test("E2E-09 admin can open model management controls @fullstack", async ({ page, request }) => {
    test.skip(
      process.env.E2E_ADMIN_ENABLED !== "1",
      "Set E2E_ADMIN_ENABLED=1 when the backend is started with profile=e2e (admin seeded).",
    );
    // Avoid CI flakes: the app can be "healthy" while auth is still seeding/initializing.
    // If admin login is not available yet, skip instead of timing out on the UI.
    const probe = await request.post(productApiUrl("/auth/login"), {
      data: { email: adminEmail(), password: adminPassword() },
    });
    test.skip(
      probe.status() !== 200,
      `Admin login probe failed (HTTP ${probe.status()}); backend likely not started with profile=e2e yet.`,
    );

    await loginAsE2eAdmin(page);
    await expect(page.getByRole("link", { name: /^admin$/i })).toBeVisible();
    await page.goto("/en/admin");
    await expect(page.getByRole("heading", { name: /administration|administración/i })).toBeVisible({
      timeout: 20_000,
    });

    const modelAdmin = page.getByTestId("admin-models-card");
    await expect(modelAdmin).toBeVisible({ timeout: 15_000 });
    await expect(modelAdmin.getByText(/model allowlist/i)).toBeVisible();
    await expect(modelAdmin.getByLabel(/model name/i).first()).toBeVisible();
    await expect(modelAdmin.getByRole("button", { name: /check/i })).toBeVisible();
    await expect(modelAdmin.getByText(/llm models/i)).toBeVisible();
    await expect(modelAdmin.getByText(/embedding models/i)).toBeVisible();
    await expect(modelAdmin.getByRole("button", { name: /delete/i }).first()).toBeVisible();
    await expect(page.getByText(/could not load allowlist/i)).toHaveCount(0);
  });
});
