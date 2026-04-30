import { expect, test } from "@playwright/test";

/**
 * Smoke E2E: no backend required — validates routing and static render of auth shells.
 */
test.describe("Public auth pages", () => {
  test("login page shows title and form @smoke", async ({ page }) => {
    await page.goto("/en/login");
    await expect(page.locator("h1")).toBeVisible();
    await expect(page.getByLabel(/email|correo/i)).toBeVisible();
  });

  test("register page shows title @smoke", async ({ page }) => {
    await page.goto("/en/register");
    await expect(page.locator("h1")).toBeVisible();
  });

  test.describe("Google OAuth CTA (@oauth — requires NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED=true at build/start)", () => {
    test("login and register show Continue with Google linking to v5 start path", async ({ page }) => {
      test.skip(
        process.env.NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED !== "true",
        "Build/start webapp with NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED=true to run OAuth UI checks.",
      );

      for (const path of ["/en/login", "/en/register"] as const) {
        await page.goto(path);
        const link = page.getByRole("link", { name: /continue with google/i });
        await expect(link).toBeVisible();
        await expect(link).toHaveAttribute("href", /\/api\/v5\/auth\/oauth\/google\/start\?locale=/);
      }
    });
  });
});
