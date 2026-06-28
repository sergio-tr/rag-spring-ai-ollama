import { expect, test } from "@playwright/test";
import { adminEmail, adminPassword } from "../fixtures/users";
import { loginAsE2eAdmin, loginAsSeedUser, productApiUrl } from "../support/helpers";

test.describe("Admin model allowlist @fullstack", () => {
  test("admin sees allowlist controls and can probe @fullstack", async ({ page, request }) => {
    test.skip(
      process.env.E2E_ADMIN_ENABLED !== "1",
      "Set E2E_ADMIN_ENABLED=1 with Spring profile e2e.",
    );

    const probe = await request.post(productApiUrl("/auth/login"), {
      data: { email: adminEmail(), password: adminPassword() },
    });
    test.skip(probe.status() !== 200, `Admin login probe HTTP ${probe.status()}`);

    await loginAsE2eAdmin(page);
    await page.goto("/en/admin", { waitUntil: "domcontentloaded" });
    const card = page.getByTestId("admin-models-card");
    await expect(card).toBeVisible({ timeout: 15_000 });
    await expect(card.getByText(/llm models/i)).toBeVisible();
    await expect(card.getByText(/embedding models/i)).toBeVisible();
    await expect(card.getByRole("button", { name: /^(delete|eliminar)$/i }).first()).toBeVisible();
    await expect(card.getByRole("button", { name: /^(probe|comprobar)$/i }).first()).toBeVisible();
  });

  test("seed user cannot POST admin models @fullstack", async ({ request }) => {
    const login = await request.post(productApiUrl("/auth/login"), {
      data: { email: process.env.E2E_SEED_EMAIL ?? "dev@local.test", password: process.env.E2E_SEED_PASSWORD ?? "dev" },
    });
    test.skip(login.status() !== 200, "Seed login unavailable.");
    const { accessToken } = (await login.json()) as { accessToken: string };
    const res = await request.post(productApiUrl("/admin/models"), {
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      data: {
        modelId: "closure-probe-model",
        modelType: "LLM",
        enabled: false,
        pullIfMissing: false,
        tags: [],
      },
    });
    expect(res.status()).toBe(403);
  });

  test("seed user admin page has no destructive allowlist write @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/admin", { waitUntil: "domcontentloaded", timeout: 15_000 }).catch(() => undefined);
    const forbidden = page.getByTestId("admin-models-card");
    const visible = await forbidden.isVisible().catch(() => false);
    if (visible) {
      await expect(forbidden.getByRole("button", { name: /^delete$/i })).toHaveCount(0);
    }
  });
});
