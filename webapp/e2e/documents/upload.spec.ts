import { expect, test } from "@playwright/test";
import { sampleTextFilePath } from "../fixtures/documents";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser, waitForDocumentReadyByName } from "../support/helpers";

/**
 * E2E-04: upload reaches READY (profile {@code e2e} uses in-process embedding stubs; no real Ollama).
 */
test.describe("Documents", () => {
  test("E2E-04 upload text file becomes READY @fullstack", async ({ page }) => {
    // Upload + indexing can legitimately take >30s in CI; poll below allows up to 120s.
    test.setTimeout(180_000);

    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-doc"));

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/);
    await expect(page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });

    await page.locator('input[type="file"]').setInputFiles(sampleTextFilePath());

    await waitForDocumentReadyByName(page, "sample.txt", 120_000);
  });
});
