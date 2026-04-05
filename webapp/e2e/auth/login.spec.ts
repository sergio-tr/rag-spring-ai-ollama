import { expect, test } from "@playwright/test";
import { seedEmail } from "../fixtures/users";
import { loginAsSeedUser } from "../support/helpers";

/**
 * E2E-01 / E2E-02 against a live Spring API + Flyway seed (V16__seed_dev_tenant.sql).
 * CI: e2e-fullstack.yml. Local: start backend with profile `e2e`, then:
 *   NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:9000 E2E_ALLOW_INSECURE_COOKIES=true npm run build && npm run test:e2e:fullstack
 */
test.describe("Auth with API", () => {
  test("E2E-01 seed user login reaches projects @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
  });

  test("E2E-02 wrong password shows invalid credentials @fullstack", async ({ page }) => {
    await page.goto("/en/login");
    await page.getByLabel(/email|correo/i).fill(seedEmail());
    await page.getByLabel(/^password$/i).fill("definitely-wrong-password-xyz");
    await page.getByRole("button", { name: /continue|iniciar|sign in/i }).click();
    await expect(page.getByRole("alert")).toContainText(
      /invalid email|incorrectos/i,
      { timeout: 15_000 },
    );
  });
});
