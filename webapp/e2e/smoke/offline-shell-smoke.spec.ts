import { expect, test, type Page } from "@playwright/test";
import { addSmokeAccessCookie, installUxSurfacesApiStub } from "./fixtures/ux-surfaces-api-stub";

async function resetSidebarPersistence(page: Page): Promise<void> {
  await page.addInitScript(() => {
    try {
      localStorage.removeItem("rag-sidebar");
      sessionStorage.removeItem("chat-conv-list-collapsed");
    } catch {
      /* ignore */
    }
  });
}

async function expectMainNavProjectsLink(page: Page): Promise<void> {
  const sidebar = page.locator("#app-sidebar, #app-sidebar-drawer");
  const mainNav = sidebar.getByRole("navigation", { name: /^main$/i });
  const projectsInNav = mainNav.getByRole("link", { name: /^(projects|proyectos)$/i });
  if (await projectsInNav.first().isVisible().catch(() => false)) {
    return;
  }

  const menuBtn = page.getByRole("button", { name: /open navigation menu/i });
  if (await menuBtn.isVisible().catch(() => false)) {
    await menuBtn.click();
    await expect(page.locator("#app-sidebar-drawer")).toBeVisible({ timeout: 5_000 });
    await expect(
      page.locator("#app-sidebar-drawer").getByRole("navigation", { name: /^main$/i }).getByRole("link", {
        name: /^(projects|proyectos)$/i,
      }).first(),
    ).toBeVisible({ timeout: 5_000 });
    return;
  }

  await expect(projectsInNav.first()).toBeVisible({ timeout: 5_000 });
}

async function gotoOfflineAccountShell(page: Page): Promise<void> {
  const toolbar = page.getByTestId("app-main-toolbar");
  const globalErrorHeading = page.getByRole("heading", { name: /this page couldn.t load/i });
  const deadline = Date.now() + 30_000;

  await page.goto("/en/settings/account", { waitUntil: "domcontentloaded", timeout: 60_000 });

  while (Date.now() < deadline) {
    const ready =
      (await toolbar.isVisible().catch(() => false)) &&
      !(await globalErrorHeading.isVisible().catch(() => false));
    if (ready) {
      return;
    }
    if (await globalErrorHeading.isVisible().catch(() => false)) {
      const reload = page.getByRole("button", { name: /^reload$/i });
      if (await reload.isVisible().catch(() => false)) {
        await reload.click();
      } else {
        await page.goto("/en/settings/account", { waitUntil: "load", timeout: 20_000 }).catch(() => undefined);
      }
      await page.waitForTimeout(400);
      continue;
    }
    await page.waitForTimeout(200);
  }

  await expect(toolbar).toBeVisible({ timeout: 3_000 });
}

/**
 * App chrome + settings/lab/data/account shells with stubbed product API (no full backend stack).
 */
test.describe("Offline shell smoke", () => {
  test.describe.configure({ mode: "serial", timeout: 60_000 });

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    try {
      await resetSidebarPersistence(page);
      await installUxSurfacesApiStub(page);
      await addSmokeAccessCookie(page);
      await page.goto("/en/settings/account", { waitUntil: "domcontentloaded", timeout: 60_000 });
      await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
    } finally {
      await page.close();
    }
  });

  test.beforeEach(async ({ page }) => {
    await resetSidebarPersistence(page);
    await installUxSurfacesApiStub(page);
    await addSmokeAccessCookie(page);
  });

  test("settings account tab renders export panel heading @smoke", async ({ page }) => {
    await page.goto("/en/settings/account", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByText(/Export my data/i).first()).toBeVisible({ timeout: 30_000 });
  });

  test("main toolbar, sidebar link, and activity tips entry render @smoke", async ({ page }) => {
    await gotoOfflineAccountShell(page);
    await expectMainNavProjectsLink(page);
    await expect(page.getByRole("button", { name: /open activity and tips/i })).toBeVisible();
    await expect(page.getByText(/Hide tips panel/i)).toHaveCount(0);
    await expect(page.getByText(/Show tips panel/i)).toHaveCount(0);
    await expect(page.getByText(/Export my data/i).first()).toBeVisible({ timeout: 10_000 });
  });

  test("main toolbar renders on mobile viewport without horizontal clip @smoke", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await gotoOfflineAccountShell(page);
    await expect(page.getByTestId("app-main-toolbar")).toBeVisible();
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
});
