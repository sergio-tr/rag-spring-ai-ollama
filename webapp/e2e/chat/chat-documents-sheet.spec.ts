import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import {
  createAndActivateProject,
  createNewChatConversation,
  loginAsSeedUser,
  openChatDocumentsSheet,
  waitForDocumentReadyByName,
} from "../support/helpers";

const DOC_NAME = "e2e-p8-sheet-doc.txt";

/**
 * Chat document scope sheet: open via Chat configuration → Manage project documents.
 * Assertions are scoped to {@link #chat-documents-sheet} to avoid duplicate global Close buttons (sheet chrome + footer).
 */
test.describe("Chat manage documents sheet @fullstack", () => {
  test("opens sheet and toggles READY document inclusion @fullstack", async ({ page }) => {
    // Document upload + ingest poll allows 120s; raise default 30s test timeout for CI stability.
    test.setTimeout(180_000);

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
    const readyDoc = await waitForDocumentReadyByName(page, DOC_NAME, 120_000);

    await page.getByRole("link", { name: /^chat$/i }).click();
    await createNewChatConversation(page);
    await expect(page.getByTestId("chat-message-composer")).toBeEnabled({ timeout: 15_000 });

    const sheet = await openChatDocumentsSheet(page);
    await expect(sheet.getByRole("heading", { name: /Documents for this chat/i })).toBeVisible({
      timeout: 15_000,
    });

    const docCheckbox = sheet.getByTestId(`chat-document-include-checkbox-${readyDoc.id}`);
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
