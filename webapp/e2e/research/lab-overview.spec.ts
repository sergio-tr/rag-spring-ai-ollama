import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * Lab root: status JSON hub (merges former product-smoke E2E-07 and lab-navigation overview checks).
 */
test.describe("Lab overview", () => {
  test("E2E-12 lab overview loads status hub and JSON @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab");
    await expect(
      page.getByRole("heading", { name: /research lab|laboratorio|lab status|estado del lab/i }),
    ).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.locator("pre")).toContainText("{", { timeout: 20_000 });
  });
});
