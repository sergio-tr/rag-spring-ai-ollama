import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import {
  createAndActivateProject,
  createNewChatConversation,
  loginAsSeedUser,
  sendChatMessage,
} from "../support/helpers";

/** E2E-10: multiple conversations and switch between them using stable list semantics (not raw button counts). */
test.describe("Conversation history", () => {
  test("E2E-10 two conversations and switch sidebar selection @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-conv"));

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);

    const suffix = `${Date.now()}`;
    const titleA = `E2E-Hist-A-${suffix}`;
    const titleB = `E2E-Hist-B-${suffix}`;

    await createNewChatConversation(page);
    await expect(page.getByTestId("chat-message-composer")).toBeEnabled({ timeout: 15_000 });
    await page.getByRole("textbox", { name: /conversation title/i }).fill(titleA);
    await page.getByRole("textbox", { name: /conversation title/i }).blur();
    await sendChatMessage(page, "First thread message");
    await expect(page.getByTestId("chat-readable-column").getByText("First thread message")).toBeVisible({
      timeout: 15_000,
    });

    await createNewChatConversation(page);
    await expect(page).toHaveURL(/conversationId=/);
    const secondConversationUrl = page.url();
    await expect(page.getByTestId("chat-message-composer")).toBeEnabled({ timeout: 15_000 });
    await page.getByRole("textbox", { name: /conversation title/i }).fill(titleB);
    await page.getByRole("textbox", { name: /conversation title/i }).blur();

    const list = page.getByTestId("conversation-list");
    await expect(list.locator('[data-testid^="conversation-item-"]')).toHaveCount(2);

    const rowA = list.locator('[data-testid^="conversation-item-"]').filter({ hasText: titleA });
    const rowB = list.locator('[data-testid^="conversation-item-"]').filter({ hasText: titleB });
    await expect(rowB).toHaveAttribute("aria-current", "true");

    await rowA.click();
    await expect.poll(async () => page.url(), { timeout: 10_000 }).not.toBe(secondConversationUrl);
    await expect(rowA).toHaveAttribute("aria-current", "true");
    await expect(rowB).not.toHaveAttribute("aria-current", "true");
  });
});
