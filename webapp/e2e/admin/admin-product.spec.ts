import { expect, test } from "@playwright/test";
import { loginAsE2eAdmin, productApiUrl } from "../support/helpers";
import { adminEmail, adminPassword } from "../fixtures/users";

/**
 * E2E-09: ADMIN user can load the provider-aware model catalog UI.
 */
test.describe("Admin product API", () => {
  test("E2E-09 admin can open model management controls @fullstack", async ({ page, request }) => {
    test.skip(
      process.env.E2E_ADMIN_ENABLED !== "1",
      "Set E2E_ADMIN_ENABLED=1 when the backend is started with profile=e2e (admin seeded).",
    );
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
    await expect(modelAdmin.getByText(/configured model catalog|catálogo de modelos configurado/i)).toBeVisible();
    await expect(modelAdmin.getByText(/llm models|modelos llm/i)).toBeVisible();
    await expect(modelAdmin.getByText(/embedding models|modelos de embedding/i)).toBeVisible();
    await expect(page.getByText(/could not load model catalog|no se pudo cargar el catálogo/i)).toHaveCount(0);
    await expect(page.getByText(/model allowlist/i)).toHaveCount(0);
  });
});
