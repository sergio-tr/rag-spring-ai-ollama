import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser, sendChatMessage } from "../support/helpers";

/**
 * Phase 8C — destructive chat action requires confirmation (sidebar delete control).
 */
test.describe("Chat delete conversation @fullstack", () => {
  test("delete chat opens dialog, confirm removes conversation @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-p8-del"));

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);

    await page.getByTestId("chat-new-conversation").click();
    await sendChatMessage(page, "delete-me-marker");

    const deleteConversationBtn = page.getByRole("button", { name: /Delete chat:/i });
    await expect(deleteConversationBtn.first()).toBeVisible({ timeout: 25_000 });
    await deleteConversationBtn.first().click();

    await expect(page.getByRole("heading", { name: /Delete this chat/i })).toBeVisible({
      timeout: 15_000,
    });
    await page.getByRole("button", { name: /^Delete chat$/i }).click();

    await expect(page.getByRole("button", { name: /Delete chat:/i })).toHaveCount(0, {
      timeout: 25_000,
    });
    await expect(page.getByText("delete-me-marker")).toHaveCount(0);
  });
});
