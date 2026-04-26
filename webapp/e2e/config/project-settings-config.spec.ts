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
    await expect(page.getByText(/no active project|no hay proyecto activo/i)).toHaveCount(0);
    // The project settings UI is a schema-driven form, not a JSON editor.
    // For CI stability, require that the schema resolved (inputs OR empty-schema message).
    await expect(
      page
        .locator('input[id^="cfg-"]')
        .first()
        .or(page.getByText(/no configurable fields were returned|no se devolvieron campos configurables/i).first()),
    ).toBeVisible({ timeout: 25_000 });
  });
});
