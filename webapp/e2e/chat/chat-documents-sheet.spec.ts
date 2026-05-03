import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser } from "../support/helpers";

const DOC_NAME = "e2e-p8-sheet-doc.txt";

/**
 * Chat document scope sheet: open via ⋮ overflow → Manage project documents.
 * Assertions are scoped to {@link #chat-documents-sheet} to avoid duplicate global Close buttons (sheet chrome + footer).
 */
test.describe("Chat manage documents sheet @fullstack", () => {
  test("opens sheet and toggles READY document inclusion @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-p8-sheet"));

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/);
    await expect(page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });

    await page.locator('input[type="file"]').setInputFiles({
      name: DOC_NAME,
      mimeType: "text/plain",
      buffer: Buffer.from("sheet inclusion marker.\n"),
    });
    await expect
      .poll(
        async () => {
          const row = page.locator("tbody tr").filter({ hasText: DOC_NAME });
          if ((await row.count()) === 0) return false;
          return (await row.getByText("READY", { exact: true }).count()) > 0;
        },
        { timeout: 120_000 },
      )
      .toBe(true);

    await page.getByRole("link", { name: /^chat$/i }).click();
    await page.getByTestId("chat-new-conversation").click();
    await expect(page.getByTestId("chat-message-composer")).toBeEnabled({ timeout: 15_000 });

    await page.getByTestId("chat-actions-menu-trigger").click();
    await page.getByTestId("chat-open-documents-sheet").click();

    const sheet = page.getByTestId("chat-documents-sheet");
    await expect(sheet.getByRole("heading", { name: /Documents for this chat/i })).toBeVisible({
      timeout: 15_000,
    });

    const checkboxLabel = new RegExp(`Include ${DOC_NAME.replace(".", "\\.")} in retrieval scope`, "i");
    const docCheckbox = sheet.getByRole("checkbox", { name: checkboxLabel });
    await expect(docCheckbox).toBeVisible();

    // ScrollArea animations/layout shifts trip Playwright "stable" checks on setChecked; click targets real UX surface.
    const docRowLabel = sheet.locator("label").filter({ hasText: DOC_NAME });
    await docRowLabel.locator('input[type="checkbox"]').click({ force: true });
    await expect
      .poll(async () => docCheckbox.isChecked(), { timeout: 20_000, intervals: [100, 250, 500] })
      .toBe(true);

    await sheet.getByTestId("chat-documents-sheet-close").click();
    await expect(sheet).toBeHidden({ timeout: 15_000 });
  });
});
