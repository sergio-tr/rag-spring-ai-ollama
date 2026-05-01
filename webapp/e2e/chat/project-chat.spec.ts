import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser } from "../support/helpers";

/**
 * E2E-03: create project, activate, open chat shell (no SSE / Ollama required).
 */
test.describe("Project and chat", () => {
  test("E2E-03 create project activate and open chat @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    const projectName = uniqueProjectName("e2e-pw");
    await createAndActivateProject(page, projectName);

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);
    await expect(
      page.getByRole("main").getByRole("button", { name: /new conversation|nueva conversación/i }),
    ).toBeVisible({ timeout: 15_000 });
  });
});
