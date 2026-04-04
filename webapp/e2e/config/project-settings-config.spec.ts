import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser } from "../support/helpers";

/**
 * Project-scoped RAG JSON editor requires an active project in the client store.
 */
test.describe("Project settings", () => {
  test("E2E-15 active project shows project config JSON @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    const name = uniqueProjectName("e2e-cfg");
    await createAndActivateProject(page, name);

    await page.goto("/en/settings/project");
    await expect(page.getByText(/^Active project$|^Proyecto activo$/i).first()).toBeVisible({
      timeout: 20_000,
    });
    await expect(
      page.getByRole("textbox", { name: /Project configuration JSON|JSON.*proyecto/i }),
    ).toBeVisible({ timeout: 25_000 });
  });
});
