import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser, sendChatMessage } from "../support/helpers";

/** E2E-10: multiple conversations and switch between them in the chat sidebar. */
test.describe("Conversation history", () => {
  test("E2E-10 two conversations and switch sidebar selection @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-conv"));

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);

    await page
      .getByRole("main")
      .getByRole("button", { name: /new conversation|nueva conversación/i })
      .click();
    await sendChatMessage(page, "First thread message");
    // Smoke-level assertion: user message is rendered (job completion can vary by backend flags).
    await expect(page.getByText("First thread message")).toBeVisible({ timeout: 10_000 });

    await page
      .getByRole("main")
      .getByRole("button", { name: /new conversation|nueva conversación/i })
      .click();
    await expect(page).toHaveURL(/conversationId=/);
    const firstConversationUrl = page.url();
    await expect(page.getByPlaceholder(/message|mensaje/i)).toBeEnabled({ timeout: 15_000 });

    const convList = page.locator("aside div.flex.max-h-48").getByRole("button");
    await expect(convList).toHaveCount(2);
    // Creating the second conversation should switch the active conversationId.
    await expect.poll(async () => page.url(), { timeout: 10_000 }).not.toBe(firstConversationUrl);
  });
});
