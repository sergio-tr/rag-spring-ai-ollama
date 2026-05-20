import { expect, test } from "@playwright/test";

/**
 * Smoke E2E: no backend required — validates routing and static render of auth shells.
 */
test.describe("Public auth pages", () => {
  test("login page shows title and form @smoke", async ({ page }) => {
    await page.goto("/en/login", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByRole("heading", { name: /^Sign in$/i })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByLabel(/^Email$/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByLabel(/^Password$/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: /show password|hide password/i })).toBeVisible({
      timeout: 30_000,
    });
  });

  test("register page shows title @smoke", async ({ page }) => {
    await page.goto("/en/register", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.getByRole("heading", { name: /^Create account$/i })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByLabel(/^Email$/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByLabel(/^Password$/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: /show password|hide password/i })).toBeVisible({
      timeout: 30_000,
    });
  });

  test("login and register pages have no locale-prefixed /api anchor hrefs @smoke", async ({ page }) => {
    for (const path of ["/en/login", "/en/register"] as const) {
      await page.goto(path, { waitUntil: "domcontentloaded", timeout: 60_000 });
      const bad = page.locator('a[href^="/en/api"], a[href^="/es/api"]');
      await expect(bad).toHaveCount(0);
    }
  });

  test.describe("Google OAuth CTA (@oauth — requires NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED=true at build/start)", () => {
    test("login and register show Continue with Google linking to v5 start path", async ({ page }) => {
      test.skip(
        process.env.NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED !== "true",
        "Build/start webapp with NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED=true to run OAuth UI checks.",
      );

      for (const path of ["/en/login", "/en/register"] as const) {
        await page.goto(path, { waitUntil: "domcontentloaded", timeout: 60_000 });
        const link = page.getByRole("link", { name: /continue with google/i });
        await expect(link).toBeVisible({ timeout: 30_000 });
        await expect(link).toHaveAttribute("href", "/api/v5/auth/oauth/google/start?locale=en");
        const href = await link.getAttribute("href");
        expect(href).not.toMatch(/^\/(en|es)\//);
      }
    });

    test("clicking Google CTA starts request to oauth start path @smoke", async ({ page }) => {
      test.skip(
        process.env.NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED !== "true",
        "Build/start webapp with NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED=true to run OAuth UI checks.",
      );
      await page.goto("/en/login", { waitUntil: "domcontentloaded", timeout: 60_000 });
      const expectedPath = "/api/v5/auth/oauth/google/start?locale=en";
      let startRequestSeen = false;

      await page.route(`**${expectedPath}`, async (route) => {
        startRequestSeen = true;
        await route.fulfill({
          status: 302,
          headers: { location: "https://accounts.google.com/o/oauth2/v2/auth" },
          body: "",
        });
      });

      await page.getByRole("link", { name: /continue with google/i }).click();
      await expect
        .poll(() => startRequestSeen, { timeout: 10_000 })
        .toBeTruthy();
    });
  });
});
