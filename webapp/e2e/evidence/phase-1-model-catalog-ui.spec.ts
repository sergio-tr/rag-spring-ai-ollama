import { expect, test } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { bootstrapBrowserSession, gotoWithProxyRetry, productApiUrl } from "../support/helpers";

const SCREENSHOT_DIR = path.resolve(
  __dirname,
  "../../../docs/evidence/phase-1-model-catalog-runtime-gate-20250701/screenshots",
);

async function capture(page: import("@playwright/test").Page, fileName: string): Promise<void> {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, fileName), fullPage: false });
}

test.describe("Phase 1 model catalog UI verification @evidence", () => {
  test.use({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });

  test("capture admin catalog and registry screenshots", async ({ page }) => {
    test.setTimeout(180_000);
    const email = process.env.E2E_ADMIN_EMAIL ?? "admin@dev.local";
    const password = process.env.E2E_ADMIN_PASSWORD ?? "dev";
    const loginRes = await page.request.post(productApiUrl("/auth/login"), {
      data: { email, password },
      headers: { "Content-Type": "application/json" },
    });
    expect(loginRes.ok(), await loginRes.text()).toBeTruthy();
    await bootstrapBrowserSession(page, (await loginRes.json()) as { accessToken: string });

    await gotoWithProxyRetry(page, "/en/admin");
    await expect(page.getByTestId("admin-models-card")).toBeVisible({ timeout: 30_000 });
    await capture(page, "01_admin_model_catalog.png");

    const catalogSection = page.getByTestId("admin-llm-catalog-section");
    if (await catalogSection.isVisible().catch(() => false)) {
      await catalogSection.scrollIntoViewIfNeeded();
      await capture(page, "02_admin_llm_catalog_section.png");
    }

    await gotoWithProxyRetry(page, "/en/settings/user");
    await expect(page.getByRole("heading", { name: /Model configuration/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    await capture(page, "03_settings_model_configuration.png");

    await gotoWithProxyRetry(page, "/en/lab/evaluation/embedding");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
    await capture(page, "04_lab_embedding_picker.png");

    await gotoWithProxyRetry(page, "/en/lab/evaluation/llm");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
    await capture(page, "05_lab_llm_picker.png");

    await gotoWithProxyRetry(page, "/en/settings/model-registry");
    const registry = page.getByTestId("model-registry-card");
    if (await registry.isVisible({ timeout: 10_000 }).catch(() => false)) {
      await capture(page, "06_model_registry_status.png");
    } else {
      await capture(page, "06_model_registry_status.png");
    }
  });
});
