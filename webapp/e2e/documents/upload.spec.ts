import { expect, test } from "@playwright/test";
import { sampleTextFilePath } from "../fixtures/documents";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser } from "../support/helpers";

/**
 * E2E-04: upload reaches READY (profile {@code e2e} uses in-process embedding stubs; no real Ollama).
 */
test.describe("Documents", () => {
  test("E2E-04 upload text file becomes READY @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-doc"));

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/);

    await page.locator('input[type="file"]').setInputFiles(sampleTextFilePath());

    await expect.poll(
      async () => {
        const row = page.locator("tbody tr").filter({ hasText: "sample.txt" });
        if ((await row.count()) === 0) return false;
        return (await row.getByText("READY", { exact: true }).count()) > 0;
      },
      { timeout: 120_000 },
    ).toBe(true);
  });
});
