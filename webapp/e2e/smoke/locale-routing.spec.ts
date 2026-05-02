import { expect, test } from "@playwright/test";

/**
 * Locale-prefix regressions (middleware dedupe + auth redirects). No backend required.
 */
test.describe("Locale routing smoke", () => {
  test("dedupes double EN locale segment on login path @smoke", async ({ page }) => {
    const res = await page.goto("/en/en/login", { waitUntil: "domcontentloaded", timeout: 60_000 });
    expect(res?.ok()).toBeTruthy();
    await expect(page).toHaveURL(/\/en\/login$/);
    await expect(page).not.toHaveURL(/\/en\/en\//);
    await expect(page.locator("h1")).toBeVisible({ timeout: 30_000 });
  });

  test("dedupes triple EN locale segment chain @smoke", async ({ page }) => {
    await page.goto("/en/en/en/login", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page).toHaveURL(/\/en\/login$/);
    await expect(page).not.toHaveURL(/\/en\/en\//);
  });

  test("dedupes double ES locale segment on login path @smoke", async ({ page }) => {
    await page.goto("/es/es/login", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page).toHaveURL(/\/es\/login$/);
    await expect(page).not.toHaveURL(/\/es\/es\//);
  });

  test("protected route without cookie redirects to single-locale login @smoke", async ({ page }) => {
    await page.goto("/en/chat", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page).toHaveURL(/\/en\/login$/);
    await expect(page).not.toHaveURL(/\/en\/en\//);
  });

  test("double-prefix protected URL dedupes before login redirect @smoke", async ({ page }) => {
    await page.goto("/en/en/settings/data", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page).toHaveURL(/\/en\/login$/);
    await expect(page).not.toHaveURL(/\/en\/en\//);
  });
});
