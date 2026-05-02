import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import { createAndActivateProject, loginAsSeedUser, sendChatMessage } from "../support/helpers";

/**
 * E2E-05: ingest a doc, then one chat turn.
 *
 * Keep @fullstack stable and fast: only require that the stub assistant reply is produced.
 * UI explainability panels (pipeline/sources) are allowed to evolve without breaking CI smoke.
 */
test.describe("Chat RAG", () => {
  test("E2E-05 send message completes with stub answer pipeline and sources @fullstack", async ({
    page,
  }) => {
    await loginAsSeedUser(page);
    await createAndActivateProject(page, uniqueProjectName("e2e-chat"));

    await page.getByRole("link", { name: /documents|documentos/i }).click();
    await expect(page).toHaveURL(/\/en\/documents/);
    await expect(page).toHaveURL(/[?&]projectId=/, { timeout: 15_000 });
    await page.locator('input[type="file"]').setInputFiles({
      name: "e2e-sources.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("SOURCE_MARKER_E2E_05 retrieval smoke content.\n"),
    });
    await expect.poll(
      async () => {
        const row = page.locator("tbody tr").filter({ hasText: "e2e-sources.txt" });
        if ((await row.count()) === 0) return false;
        return (await row.getByText("READY", { exact: true }).count()) > 0;
      },
      { timeout: 120_000 },
    ).toBe(true);

    await page.getByRole("link", { name: /^chat$/i }).click();
    await expect(page).toHaveURL(/\/en\/chat/);

    await page.getByTestId("chat-new-conversation").click();
    await sendChatMessage(page, "What is in my project documents?");

    await expect(page.getByText(/could not send message/i)).toHaveCount(0);
    await expect(page.getByText(/E2E stub reply/i)).toBeVisible({ timeout: 120_000 });
  });
});
