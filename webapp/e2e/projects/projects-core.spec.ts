import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser } from "../support/helpers";

/**
 * Projects list, create dialog, auto-activate, documents require active project.
 */
test.describe("Projects core", () => {
  test("E2E-S5-01 projects list and create activates @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await expect(page.getByRole("heading", { name: /^projects$/i })).toBeVisible();
    const name = uniqueProjectName("e2e-s5");
    await createAndActivateProject(page, name);
    await expect(
      page.locator('[data-slot="card"]').filter({ hasText: name }).getByRole("button", { name: /^(Active|Activo)$/i }),
    ).toBeVisible();
  });

  test("E2E-S5-02 documents page loads @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    const name = uniqueProjectName("e2e-s5-docs");
    await createAndActivateProject(page, name);
    await page.goto("/en/documents");
    await expect(page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });
    await expect(page.getByRole("heading", { name: /^documents$/i })).toBeVisible({ timeout: 15_000 });
  });
});
