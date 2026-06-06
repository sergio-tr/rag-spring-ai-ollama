import { expect, test } from "@playwright/test";
import {
  addSmokeAccessCookie,
  installMinimalProductApiStub,
} from "./fixtures/minimal-product-api-stub";

/**
 * App chrome + settings/lab/data/account shells with stubbed product API (no full backend stack).
 */
test.describe("Offline shell smoke", () => {
  test.beforeEach(async ({ page }) => {
    await installMinimalProductApiStub(page);
    await addSmokeAccessCookie(page);
  });

  test("main toolbar, sidebar link, and activity tips entry render @smoke", async ({ page }) => {
    await page.goto("/en/settings", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByTestId("app-main-toolbar")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("link", { name: /^projects$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /open activity and tips/i })).toBeVisible();
    await expect(page.getByText(/Hide tips panel/i)).toHaveCount(0);
    await expect(page.getByText(/Show tips panel/i)).toHaveCount(0);
  });

  test("main toolbar renders on mobile viewport without horizontal clip @smoke", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/en/settings", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByTestId("app-main-toolbar")).toBeVisible({ timeout: 30_000 });
    const clip = await page.evaluate(() => {
      const el = document.documentElement;
      return { scrollWidth: el.scrollWidth, clientWidth: el.clientWidth };
    });
    expect(clip.scrollWidth).toBeLessThanOrEqual(clip.clientWidth + 1);
  });

  test("settings tab query normalizes to segmented URL @smoke", async ({ page }) => {
    await page.goto("/en/settings?tab=user", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page).toHaveURL(/\/en\/settings\/user/, { timeout: 15_000 });
  });

  test("settings URL survives back-forward between general and user tab @smoke", async ({ page }) => {
    await page.goto("/en/settings", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByRole("heading", { name: /settings/i }).first()).toBeVisible({
      timeout: 30_000,
    });
    await page.goto("/en/settings/user", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page).toHaveURL(/\/en\/settings\/user/);
    await page.goBack();
    await expect(page).toHaveURL(/\/en\/settings$/);
    await page.goForward();
    await expect(page).toHaveURL(/\/en\/settings\/user/);
  });

  test("lab overview renders stubbed status hub @smoke", async ({ page }) => {
    await page.goto("/en/lab", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByRole("heading", { name: /^Research Lab$/i })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByTestId("lab-overview-compact")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId("lab-workflow-card-llm")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId("lab-overview-status-technical")).toContainText(
      /Technical details|Detalles técnicos/i,
    );
  });

  test("settings data tab renders summary card @smoke", async ({ page }) => {
    await page.goto("/en/settings/data", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByText(/Your usage summary/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Totals for projects and conversations/i)).toBeVisible();
  });

  test("settings account tab renders export panel heading @smoke", async ({ page }) => {
    await page.goto("/en/settings/account", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByText(/Export my data/i).first()).toBeVisible({ timeout: 30_000 });
  });
});
