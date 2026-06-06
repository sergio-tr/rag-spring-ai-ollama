import { expect, test } from "@playwright/test";
import { loginAsSeedUser } from "../support/helpers";

/**
 * Lab root: typed readiness hub + Raw JSON (`countsByDatasetKind`, experimental uploads panel).
 */
test.describe("Lab overview", () => {
  test("E2E-12 lab overview loads status hub and JSON @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await page.goto("/en/lab");
    await expect(
      page.getByRole("heading", { name: /^Research Lab$/i }),
    ).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByTestId("lab-overview-status-technical")).toBeVisible();
    await expect(page.locator("pre")).toContainText("{", { timeout: 20_000 });
  });
});
