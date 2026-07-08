import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import {
  createAndActivateProject,
  createNewChatConversation,
  loginAsSeedUser,
  openChatConfigurationPanel,
} from "../support/helpers";

/**
 * Destructive chat delete from the current Chat configuration panel with confirmation dialog.
 * Uses a unique conversation title scoped to the conversation list - no global getByText on message bodies.
 */
test.describe("Chat delete conversation @fullstack", () => {
  test("delete chat opens dialog, confirm removes conversation @fullstack", async ({ page }) => {
    // Conversation PATCH + list refresh may exceed 30s on cold CI runs.
    test.setTimeout(120_000);

    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-p8-del"));

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);

    await createNewChatConversation(page);

    const composer = page.getByTestId("chat-message-composer");
    await expect(composer).toBeVisible({ timeout: 25_000 });
    await expect(composer).toBeEnabled({ timeout: 25_000 });

    const uniqueTitle = `E2E-DEL-${Date.now()}`;
    const titleInput = page.getByRole("textbox", { name: /conversation title/i });
    await titleInput.fill(uniqueTitle);
    await titleInput.blur();

    const list = page.getByTestId("conversation-list");
    // Row title matches delete trigger aria-label ("Delete chat: …"); target the conversation row only.
    const convRow = list.locator('[data-testid^="conversation-item-"]').filter({ hasText: uniqueTitle });
    await expect
      .poll(async () => convRow.count(), {
        timeout: 45_000,
        intervals: [150, 300, 500],
      })
      .toBe(1);
    await expect(convRow).toBeVisible();

    const panel = await openChatConfigurationPanel(page);
    await panel.getByTestId("chat-delete-menu-item").click();

    const dialog = page.getByTestId("chat-delete-confirm-dialog");
    await expect(dialog.getByRole("heading", { name: /Delete this chat|Eliminar este chat/i })).toBeVisible({
      timeout: 15_000,
    });
    await dialog.getByTestId("chat-delete-confirm-button").click();

    await expect(list.locator('[data-testid^="conversation-item-"]').filter({ hasText: uniqueTitle })).toHaveCount(
      0,
      { timeout: 25_000 },
    );
  });
});
