import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser } from "../support/helpers";

/** E2E-10: multiple conversations and switch between them in the chat sidebar. */
test.describe("Conversation history", () => {
  test("E2E-10 two conversations and switch sidebar selection @fullstack", async ({ page }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-conv"));

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);

    await page.getByRole("button", { name: /new conversation|nueva conversación/i }).click();
    const textarea = page.getByPlaceholder(/message|mensaje/i);
    await expect(textarea).toBeEnabled({ timeout: 15_000 });
    await textarea.fill("First thread message");
    await page.getByRole("button", { name: /^send$|^enviar$/i }).click();
    await expect(page.getByText(/E2E stub reply/i)).toBeVisible({ timeout: 90_000 });

    await page.getByRole("button", { name: /new conversation|nueva conversación/i }).click();
    await expect(
      page.getByText(/create or choose|crea o elige una conversación/i),
    ).toBeVisible({ timeout: 15_000 });

    const convList = page.locator("aside div.flex.max-h-48").getByRole("button");
    await expect(convList).toHaveCount(2);

    await convList.nth(1).click();
    await expect(page.getByText(/E2E stub reply/i)).toBeVisible();

    await convList.nth(0).click();
    await expect(
      page.getByText(/create or choose|crea o elige una conversación/i),
    ).toBeVisible();
  });
});
