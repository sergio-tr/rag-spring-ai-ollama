import { expect, test } from "@playwright/test";

/**
 * Smoke E2E: no backend required — validates routing and static render of auth shells.
 */
test.describe("Public auth pages", () => {
  test("login page shows title and form @smoke", async ({ page }) => {
    await page.goto("/en/login", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.locator("h1")).toBeVisible({ timeout: 30_000 });
    await expect(page.locator("#email")).toBeVisible({ timeout: 30_000 });
  });

  test("register page shows title @smoke", async ({ page }) => {
    await page.goto("/en/register", { waitUntil: "domcontentloaded", timeout: 60_000 });
    await expect(page.locator("h1")).toBeVisible({ timeout: 30_000 });
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
        // The href MUST be the absolute backend route, NOT prefixed by the active
        // locale segment. Regression guard for the bug where next-intl <Link>
        // rewrote the href to /en/api/v5/auth/oauth/google/start (HTTP 404).
        await expect(link).toHaveAttribute(
          "href",
          /^\/api\/v5\/auth\/oauth\/google\/start\?locale=(en|es)$/,
        );
        // Belt-and-suspenders: the rendered href must not start with /en/ or /es/.
        const href = await link.getAttribute("href");
        expect(href).not.toMatch(/^\/(en|es)\//);
      }
    });
  });
});
