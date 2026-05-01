import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser } from "../support/helpers";

/**
 * Phase 8C — chat-side document scope UI (sheet + READY doc checkbox), without requiring upload inside the sheet.
 */
test.describe("Chat manage documents sheet @fullstack", () => {
  test("opens sheet and toggles READY document inclusion @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-p8-sheet"));

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/);

    await page.locator('input[type="file"]').setInputFiles({
      name: "e2e-p8-sheet-doc.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("sheet inclusion marker.\n"),
    });
    await expect
      .poll(
        async () => {
          const row = page.locator("tbody tr").filter({ hasText: "e2e-p8-sheet-doc.txt" });
          if ((await row.count()) === 0) return false;
          return (await row.getByText("READY", { exact: true }).count()) > 0;
        },
        { timeout: 120_000 },
      )
      .toBe(true);

    await page.getByRole("link", { name: /^chat$/i }).click();
    await page
      .getByRole("main")
      .getByRole("button", { name: /new conversation|nueva conversación/i })
      .click();

    await page.getByRole("button", { name: /manage project documents/i }).click();
    await expect(page.getByRole("heading", { name: /Documents for this chat/i })).toBeVisible({
      timeout: 15_000,
    });

    const docCheckbox = page.getByRole("checkbox", {
      name: /Include e2e-p8-sheet-doc\.txt in retrieval scope/i,
    });
    await expect(docCheckbox).toBeVisible();
    await docCheckbox.check();
    await expect(docCheckbox).toBeChecked();

    await page.getByRole("button", { name: /^Close$/i }).click();
    await expect(page.getByRole("heading", { name: /Documents for this chat/i })).not.toBeVisible();
  });
});
